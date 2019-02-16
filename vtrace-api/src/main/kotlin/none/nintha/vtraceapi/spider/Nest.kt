package none.nintha.vtraceapi.spider

import none.nintha.vtraceapi.util.HttpSender
import org.apache.logging.log4j.util.Strings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.*
import java.util.function.Supplier

class Nest {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
        private const val STORAGE_PATH = "storage/proxy.data"
        private const val LINE_SEPARATOR = "\n"
        val PROXY_MAP: ConcurrentHashMap<String, Spider> = ConcurrentHashMap()
        val MAIN_QUEUE: Queue<Spider> = ConcurrentLinkedQueue()
        val threadPool: ExecutorService = Executors.newFixedThreadPool(24)

        init {
            CompletableFuture.runAsync { loadData() }
        }

        private fun loadData() {
            val exist: Boolean = Paths.get(STORAGE_PATH).let { Files.exists(it) }
            if (!exist) {
                logger.warn("'$STORAGE_PATH' is not existed.")
                return
            }

            val ias = Paths.get(STORAGE_PATH).toFile().readLines()
            val valids = ias.filter { isNonInNest(it) }.map {
                CompletableFuture.supplyAsync(Supplier { Pair(it, this.check(it)) }, threadPool)
            }.asSequence().map { it.get() }.filter { it.second }.map { it.first }.toSet()

            logger.info("[loadData] storage size=${valids.size}")
            addProxys(valids)
        }

        fun saveData() {
            val lines = PROXY_MAP.keys().toList().joinToString(separator = LINE_SEPARATOR)
            val path = Paths.get(STORAGE_PATH)
            Files.createDirectories(path.parent)
            path.toFile().writeText(lines)
            logger.info("[saveData] storage size=${PROXY_MAP.size}")
        }

        private fun check(proxyAddress: String): Boolean {
            try {
                val strs = proxyAddress.split(":")
                val html = HttpSender.get(HttpSender.TEST_URL, strs[0], strs[1].toInt())
                return Strings.isNotBlank(html)
            } catch (e: Exception) {
                return false
            }
        }


        fun addProxys(proxys: Collection<String>) {
            val num = proxys.toSet().asSequence().filter { !PROXY_MAP.containsKey(it) }.map {
                val parts = it.split(":")
                Spider(parts[0], parts[1].toInt())
            }.map { PROXY_MAP[it.getProxyAddress()] = it;MAIN_QUEUE.offer(it) }.count()
            logger.info("[Nest add proxys] num=$num, total=${PROXY_MAP.size}")
        }

        fun getSpider(): Spider {
            while (true) {
                val spider: Spider? = MAIN_QUEUE.poll()
                if (spider == null) {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(200) + 50)
                    continue
                }
                if (spider.failTimes >= Spider.MAX_FAIL_TIMES) {
                    PROXY_MAP.remove(spider.getProxyAddress())
                    continue
                }
                return spider
            }
        }

        fun returnSpider(spider: Spider) {
            if (spider.proxyIp == HttpSender.LOCALHOST) return

            logger.debug("[Nest] return none.nintha.vtraceapi.spider=${spider.getProxyAddress()}")
            MAIN_QUEUE.offer(spider)
        }

        fun isEmpty(): Boolean = MAIN_QUEUE.isEmpty()

        fun isNonInNest(proxyAddress: String): Boolean {
            return !PROXY_MAP.containsKey(proxyAddress)
        }
    }
}