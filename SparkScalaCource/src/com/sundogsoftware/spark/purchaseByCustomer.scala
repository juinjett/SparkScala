package com.sundogsoftware.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._

object purchaseByCustomer {
  def parseLine(line: String) ={
    val fields = line.split(",")
    val customer = fields(0)
    val price = fields(2).toFloat
    (customer, price)
  }
  
  def main(args: Array[String]) {
    Logger.getLogger("org").setLevel(Level.ERROR)
    val sc = new SparkContext("local[*]", "purchaseByCustomer")
    val lines = sc.textFile("../customer-orders.csv")
    val result = lines.map(parseLine).reduceByKey((x,y) => x+y)
    val reverse = result.map(x => (x._2, x._1)).sortByKey().collect
    for (each <- reverse) {
      val id = each._2
      val price = each._1
      println(s"$id, $price")
    }
  }
}