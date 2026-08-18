# So sánh chuyên sâu về hiệu năng: WebFlux vs Web MVC khi Streaming Tokens (LLM Integration)

Khi tích hợp các mô hình ngôn ngữ lớn (LLM) vào ứng dụng backend, đặc biệt là khi trả về dữ liệu dưới dạng luồng (Server-Sent Events - SSE) cho giao diện người dùng theo thời gian thực (real-time stream), kiến trúc backend đóng vai trò quyết định đến hiệu năng, tài nguyên hệ thống và trải nghiệm người dùng.

Dưới đây là sự khác biệt cốt lõi giữa hai mô hình: **Spring WebFlux (Reactive/Non-blocking)** và **Spring Web MVC (Synchronous/Blocking)**.

## 1. Mô hình xử lý luồng (Thread Model)

**Spring Web MVC (Mô hình Servlet truyền thống):**

- **Thread-per-request:** Với mỗi kết nối HTTP tới API, Web MVC phân bổ một thread độc lập từ Thread Pool (ví dụ: Tomcat's pool với mặc định khoảng 200 threads).
- **Vấn đề khi streaming:** Việc gọi LLM API và đợi từng token trả về có độ trễ lớn (I/O Bound). Do tính chất block, thread phục vụ request đó sẽ bị "đóng băng" (blocked), không thể phục vụ các request khác trong suốt thời gian kết nối SSE đang mở.
- **Hệ quả:** Nếu có 200 người dùng đồng thời gửi yêu cầu lên theo dõi sự cố (Incident stream), Thread Pool sẽ nhanh chóng bị cạn kiệt (exhaustion). Ứng dụng không thể tiếp nhận thêm bất kỳ request nào cho đến khi luồng stream của ai đó kết thúc.

**Spring WebFlux (Mô hình Non-blocking / Event-Loop):**

- **Event-Loop (như Netty):** Sử dụng một số lượng nhỏ các Worker Threads (thường bằng số nhân CPU) để xử lý toàn bộ các requests. Không có cơ chế 1-request-1-thread.
- **Cách Streaming Token hoạt động:** WebFlux ủy quyền thao tác I/O cho hệ điều hành. Khi có token mới trả về từ mô hình AI, framework sẽ gọi callback (Reactor's map/filter) và gửi dữ liệu về client, sau đó giải phóng thread ngay lập tức để xử lý tác vụ khác. Tính kết nối (socket) vẫn được giữ vững nhưng thread không bị giam giữ hay đợi chờ (waiting/blocking).
- **Hệ quả:** Dù có 10,000 kết nối stream đang mở (10K concurrent SSE requests), WebFlux vẫn chạy ổn định với chỉ 4-8 threads, lượng RAM tiêu thụ cực kỳ nhỏ do cấu trúc dữ liệu không đòi hỏi cấp phát context cho Thread. Hệ thống không bị thắt cổ chai (bottleneck) tại Thread Pool.

## 2. Tiêu thụ Tài nguyên Hệ thống (Memory & CPU)

- **Memory (RAM):**
  - Web MVC: Mỗi Java Thread tiêu tốn khoảng 1MB bộ nhớ stack. Càng nhiều request chờ stream token, RAM tăng tuyến tính. (200 threads tốn ~200MB riêng cho context).
  - WebFlux: Kết nối được duy trì qua socket file descriptors, RAM tiêu thụ thấp. 10,000 kết nối chỉ tốn thêm rất ít RAM so với hàng nghìn Threads.
- **Vấn đề Context Switching (CPU):**
  - Web MVC: Việc hệ điều hành liên tục chuyển đổi ngữ cảnh (Context Switch) giữa hàng trăm/nghìn threads chờ I/O làm lãng phí chu kỳ CPU.
  - WebFlux: Thread pool rất nhỏ (Event Loop threads), CPU không bị tiêu hao cho việc Context Switch mà hoàn toàn tập trung xử lý tín hiệu mạng và gửi data về phía người dùng.

## 3. Khả năng đàn hồi (Resilience & Backpressure)

- **Web MVC:** Nếu LLM tạo token quá nhanh (hoặc quá chậm), luồng stream đẩy ra không tự điều tiết tốt và có nguy cơ gây tràn bộ nhớ đệm (buffer overflow) hay đứt gãy kết nối mạng một cách âm thầm.
- **WebFlux (Project Reactor):** Hỗ trợ cơ chế **Backpressure** (áp lực ngược). Nếu phía Client kết nối yếu (e.g. mobile 3G) nhận SSE stream bị chậm, WebFlux có khả năng điều tiết thông báo ngược lên framework rằng hãy chậm lại việc request token hoặc đệm (buffer) hợp lý để không sập server do memory.

## 4. Hiện tượng Nginx Buffering

- Một chi tiết kỹ thuật thường gặp khi triển khai API LLM Stream là proxy ngược như Nginx sẽ giữ lại các token trong buffer để chờ đủ một gói lớn mới trả về Client, phá vỡ tính realtime của chữ (chunk-by-chunk stream).
- WebFlux cho phép gắn dễ dàng các tín hiệu HTTP header như `X-Accel-Buffering: no` hoặc tùy chỉnh `Flush` dễ dàng hơn vào Reactive ServerHttpResponse nhằm ép Nginx nhả luồng trực tiếp ngay khi có từng từ được sinh ra.

## 🌟 Tổng kết

Khi chạy API sinh ngôn ngữ / Chatbots (thời gian xử lý kéo dài và trả về liên tục nhiều token):

- **Web MVC:** Chỉ phù hợp khi áp dụng lượng truy cập nội bộ, cực thấp hặc sử dụng JDK 21 Virtual Threads (Project Loom) nhưng bản chất vẫn là mô hình synchronous/imperative, tiềm ẩn overhead phức tạp.
- **WebFlux (Sợi lựa chọn số 1):** Sinh ra để giải quyết hệ thống I/O Bound nặng và Streaming (SSE, WebSocket). Giúp Ban điều hành Logistics giám sát được luồng AI nhanh, tiết kiệm resource nhất có thể trên server. Đảm bảo giao diện mượt mà và không đơ do timeout hay thiếu threads.
