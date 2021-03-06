package com.cloudwick.cassandra.reads

import org.slf4j.LoggerFactory
import java.util.concurrent.{Executors, ExecutorService}
import java.util.concurrent.atomic.AtomicLong
import com.cloudwick.cassandra.OptionsConfig
import com.cloudwick.generator.utils.Utils

/**
 * Performs random reads using pool of threads
 * @author ashrith 
 */
class ReadsConcurrent(totalEvents: Long, config: OptionsConfig) extends Runnable {
  lazy val logger = LoggerFactory.getLogger(getClass)
  val threadPool: ExecutorService = Executors.newFixedThreadPool(config.threadPoolSize)
  val finalCounter:AtomicLong = new AtomicLong(0L)
  val queriesPerThread = totalEvents / config.threadCount
  val utils = new Utils

  def customerDataSetSize = config.customerDataSetSize

  def buildQuerySet: Map[String, String] = {
    logger.debug("Building query sets")
    Map(
      "query1" -> new String(s"SELECT movie_name, pt, ts FROM ${config.keyspaceName}.watch_history " +
        "WHERE cid=CUSTID;"),
      "query2" -> new String(s"SELECT movie_name, customer_name, rating " +
        s"FROM ${config.keyspaceName}.customer_rating WHERE cid=CUSTID;"),
      "query3" -> new String(s"SELECT customer_name, mid, movie_name " +
        s"FROM ${config.keyspaceName}.customer_queue WHERE cid=CUSTID;"),
      "query4" -> new String(s"SELECT movie_name, duration " +
        s"FROM ${config.keyspaceName}.movies_genre WHERE genre='GENRE' AND release_year=RYEAR AND mid=MID;")
    )
  }

  def run() = {
    utils.time(s"reading $totalEvents") {
      try {
        (1 to config.threadCount).foreach { threadCount =>
          logger.debug("Initializing thread{}", threadCount)
          threadPool.execute(
            new Reads(queriesPerThread, buildQuerySet, finalCounter, customerDataSetSize, config)
          )
        }
      } finally {
        threadPool.shutdown()
      }
      while(!threadPool.isTerminated) {
        // print every 10 seconds how many documents have been inserted
        Thread.sleep(10 * 1000)
        println("Records read: " + finalCounter)
      }
      logger.info("Total read queries executed by {} thread(s): {}", config.threadCount, finalCounter)
    }
  }
}
