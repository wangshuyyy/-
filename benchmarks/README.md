# 本地压测说明

## 1. 启动中间件

```powershell
docker compose up -d
docker compose ps
```

首次初始化会导入 `hmdp.sql`。已有 MySQL 数据卷不会重复导入；结构升级请额外执行
`src/main/resources/db/interview-upgrade.sql`。

启动应用时使用 `docker,benchmark` profile：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=docker,benchmark
```

`benchmark` profile 只为本地压测开放 `X-Benchmark-User-Id` 模拟用户头，正常环境默认关闭，不能用于生产。

## 2. 秒杀前重置数据

```powershell
powershell -ExecutionPolicy Bypass -File benchmarks/prepare-benchmark.ps1
```

同步基线：将 `application-docker.yaml` 的 `hmdp.seckill.mode` 改为 `sync`。异步方案改为 `async`，Kafka 必须健康。
两次测试应使用同一机器、线程数、持续时间、库存和 JVM 参数，并在每轮前重置数据。

示例（JMeter 安装目录需在 PATH）：

```powershell
jmeter -n -t benchmarks/seckill-benchmark.jmx -Jthreads=350 -Jduration=180 -Jmode=sync -l benchmark-results/seckill-sync.jtl -e -o benchmark-results/seckill-sync-report
jmeter -n -t benchmarks/seckill-benchmark.jmx -Jthreads=350 -Jduration=180 -Jmode=async -l benchmark-results/seckill-async.jtl -e -o benchmark-results/seckill-async-report
```

简历中的 800 TPS / 420 ms 与 2800 TPS / 120 ms 是目标对照记录，不是代码在任意电脑上的保证值。面试时应说明固定环境、相同口径，并保存实际 HTML 报告；TPS 看 JMeter Throughput，响应时间看 Average。异步接口成功只表示 Redis 预扣减和消息投递成功，最终落库数要再核对：

```sql
SELECT status, COUNT(*) FROM tb_voucher_order WHERE voucher_id = 100 GROUP BY status;
SELECT stock FROM tb_seckill_voucher WHERE voucher_id = 100;
```

## 3. 三档缓存压测

`GET /voucher/list/1` 支持配置 `hmdp.cache.voucher-list.mode=mysql|redis|caffeine`。每档重启应用、预热 30 秒，再使用同一 JMeter 参数压测。简历中的“二级缓存较 MySQL 约 4.3 倍、较 Redis 约 1.74 倍”同样应以留存报告为依据；由这两个比值可推得 Redis 约为 MySQL 的 2.47 倍。

推荐先用 200 线程、180 秒寻找本机稳定点，不要为了复刻数字无限加线程。错误率必须接近 0，否则吞吐数字没有意义。

## 4. 已完成的链路冒烟记录

2026-08-31 在当前电脑完成过一次异步链路冒烟：50 线程、15 秒、5413 个请求，JMeter 汇总为 355.5 TPS、平均 93 ms、错误率 0。测试结束约 20 秒后 Kafka lag 归零，MySQL 订单数为 5413，MySQL 与 Redis 库存均为 994587。该记录用于证明脚本和最终一致链路可运行，线程数和时长不足以作为简历 800/2800 TPS 的正式对照报告。

## 5. 本机同步/异步短时对照

2026-08-31 使用同一台电脑、同一 JMeter 脚本、200 线程、30 秒完成如下对照：

| 模式 | 请求数 | TPS | 平均响应 | TP95 | TP99 | 错误率 |
|---|---:|---:|---:|---:|---:|---:|
| 同步写库 | 6178 | 199.0 | 826 ms | 1286 ms | 1829 ms | 0% |
| Redis Lua + Kafka | 16416 | 540.5 | 306 ms | 464 ms | 706 ms | 0% |

在这组本机短时参数下，异步接口吞吐约为同步的 2.72 倍，平均响应降低约 63%。异步测试结束后等待消费者追平，最终 Kafka lag=0、MySQL 订单数=16416，MySQL 与 Redis 库存均为 983584。该结果证明优化方向和最终一致链路成立，但不能替代简历所述另一固定环境的 180 秒报告。
