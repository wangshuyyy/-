# 云游智慧行 / 黑马点评二次开发

本仓库是学习型开源项目改造版，重点实现 Redis + Lua + Kafka 异步秒杀、Caffeine + Redis 多级缓存、滑动窗口限流、模拟支付、超时关单和库存补偿。不是生产上线系统，不接入真实支付。

## 快速启动

1. 启动 MySQL、Redis、Kafka：`docker compose up -d`
2. 第一次创建数据库时会自动导入 `src/main/resources/db/hmdp.sql`。
3. 如果使用已有旧数据库，执行 `src/main/resources/db/interview-upgrade.sql`。
4. 用 `docker` profile 启动应用：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

默认 profile 会关闭 Kafka 并使用同步秒杀，便于只查看原有功能；`docker` profile 会启用 Kafka 并使用异步秒杀。缓存模式可通过 `hmdp.cache.voucher-list.mode=mysql|redis|caffeine` 切换。

Compose 对外端口为 MySQL `3307`、Redis `6380`、Kafka `9092`，避免和电脑上常见的本地 MySQL/Redis 冲突。

## 压测

压测准备、JMeter 参数和数据核对方式见 `benchmarks/README.md`。压测专用用户头仅在 `benchmark` profile 开启：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=docker,benchmark
```

不要在非压测环境开启 `benchmark` profile。
