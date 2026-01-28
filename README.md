# xMessageServer - High Performance Distributed IM Server

[English](#english) | [中文](#chinese)

<a name="english"></a>

## 🚀 Introduction

xMessageServer is a cloud-native, high-performance distributed Instant Messaging (IM) server designed to handle **millions of concurrent connections (C1000K)**. Built on **Netty** and **Spring Boot**, it leverages the power of **JDK 21 Virtual Threads** and **Redis Stream** to provide a robust, scalable, and low-latency messaging infrastructure.

Whether for private chats, massive group chats, or cross-region communication, xMessageServer delivers carrier-grade stability and message reliability (QoS 1).

## ✨ Key Features

### 1. High Performance & C1000K Support
- **Netty-based NIO**: Utilizes Netty's asynchronous event-driven architecture to handle massive concurrent TCP/WebSocket connections with minimal resource footprint.
- **Virtual Threads (JDK 21)**: Replaces traditional thread pools with lightweight Virtual Threads for message processing, significantly boosting throughput for I/O-intensive tasks (Redis/DB operations).
- **Zero-Copy & Memory Optimization**: Optimized buffer management to reduce GC pressure under high load.

### 2. High Reliability (QoS 1: At-Least-Once)
- **ACK Mechanism**: Every message sent is cached in Redis until a client ACK is received.
- **Automatic Re-transmission**:
  - **On Reconnect**: Automatically pushes all unacknowledged and offline messages when a user logs in.
  - **On Heartbeat**: Periodically checks and resends timed-out messages during Ping/Pong cycles (Lazy Check).
- **Persistent Cross-Node Messaging**: Uses **Redis Stream** instead of Pub/Sub for inter-instance communication, ensuring no messages are lost even during network jitters or instance restarts.

### 3. Distributed & Scalable Architecture
- **Stateless Design**: Instances are loosely coupled; user sessions are managed via a distributed Redis Session Store.
- **Cluster Management**: Dynamic node discovery and health checking.
- **Smart Routing**: Supports multi-device login and cross-instance message forwarding.
- **Massive Group Chat**: Optimized parallel broadcasting using Virtual Threads to handle message fan-out for large groups (5000+ members).

## 🛠 Technology Stack

- **Core**: Java 21, Spring Boot 3.x
- **Network**: Netty 4.1 (WebSocket/TCP)
- **Concurrency**: JDK 21 Virtual Threads (Project Loom)
- **Storage & Broker**: Redis (Cluster Mode Recommended)
  - *Redis Stream*: For reliable cross-instance messaging.
  - *Redis Hash/Set*: For session management and group metadata.
- **Protocol**: Custom JSON-based Protocol / WebSocket

## 🏗 Architecture

```mermaid
graph TD
    Client_A[Client A] -->|WebSocket| Node_1[IM Instance 1]
    Client_B[Client B] -->|WebSocket| Node_2[IM Instance 2]
    
    Node_1 <-->|Read/Write| Redis[(Redis Cluster)]
    Node_2 <-->|Read/Write| Redis
    
    subgraph "Redis Cluster"
        Session[User Sessions]
        MsgCache[ACK Cache]
        Stream[Redis Stream (Inter-Node Msg)]
    end
    
    Node_1 --"Forward Msg (XADD)"--> Stream
    Stream --"Consume (XREAD)"--> Node_2
```

## 🚦 Getting Started

### Prerequisites
- JDK 21+
- Maven 3.x
- Redis 5.0+

### Build & Run
```bash
# Build the project
mvn clean package -DskipTests

# Run the server
java -jar target/im-server-1.0.0.jar
```

---

<a name="chinese"></a>

## 🚀 项目介绍

xMessageServer 是一个云原生、高性能的分布式即时通讯（IM）服务端，专为处理**百万级并发连接 (C1000K)** 而设计。基于 **Netty** 和 **Spring Boot** 构建，充分利用 **JDK 21 虚拟线程** 和 **Redis Stream** 的特性，提供稳健、可扩展且低延迟的消息基础设施。

无论是单聊、万人群聊，还是跨地域通信，xMessageServer 都能提供电信级的稳定性和消息可靠性 (QoS 1)。

## ✨ 核心特性

### 1. 高性能与百万并发 (C1000K)
- **Netty 核心**: 采用 Netty 异步事件驱动架构，以极低的资源占用处理海量 TCP/WebSocket 长连接。
- **虚拟线程 (JDK 21)**: 全面引入轻量级虚拟线程替代传统线程池处理业务逻辑，在处理 Redis/DB 等 I/O 密集型任务时吞吐量大幅提升。
- **内存优化**: 优化的 Buffer 管理，减少高负载下的 GC 压力。

### 2. 高可靠投递 (QoS 1: 至少一次)
- **ACK 确认机制**: 发送的消息会暂存在 Redis 中，直到收到客户端的 ACK 确认包。
- **自动重发策略**:
  - **断线重连**: 用户登录时自动全量补发未确认消息和离线消息。
  - **心跳检测**: 利用 Ping/Pong 心跳周期，懒加载检查并重发超时未确认的消息。
- **持久化跨服通信**: 使用 **Redis Stream** 替代传统的 Pub/Sub 进行跨实例消息转发，确保即使在网络抖动或接收端重启时，跨服消息也不会丢失。

### 3. 分布式与弹性伸缩
- **无状态设计**: 实例松耦合，用户会话通过 Redis 统一管理。
- **集群管理**: 动态节点发现与健康检查，支持水平扩展。
- **智能路由**: 支持多端登录消息同步和跨实例精准投递。
- **大群优化**: 利用虚拟线程并行分发群消息，有效解决大群（5000+人）的消息写扩散延迟问题。

## 🛠 技术栈

- **核心**: Java 21, Spring Boot 3.x
- **网络层**: Netty 4.1 (WebSocket/TCP)
- **并发模型**: JDK 21 Virtual Threads (Project Loom)
- **存储与中间件**: Redis (推荐集群模式)
  - *Redis Stream*: 用于可靠的跨实例消息总线。
  - *Redis Hash/Set*: 用于会话管理、群组元数据和消息缓存。
- **协议**: 自定义 JSON 协议 / WebSocket

## 📦 部署建议

对于生产环境百万级连接，建议配置：
- **OS**: Linux (调整 `ulimit -n` > 1000000, 优化 `fs.file-max` 和 TCP 参数)。
- **Redis**: 建议使用 Redis Cluster，且开启 AOF 持久化。
- **负载均衡**: 推荐使用 Cloudflare 或自建 Dispatch 服务进行 DNS/IP 调度，实现就近接入。

### 快速启动
```bash
# 编译
mvn clean package -DskipTests

# 启动
java -jar target/im-server-1.0.0.jar
```
