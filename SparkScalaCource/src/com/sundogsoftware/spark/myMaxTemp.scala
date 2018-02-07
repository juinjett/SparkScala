package com.sundogsoftware.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._
import scala.math._

object myMaxTemp {
  def parseLine (line: String) = {
    val field = line.split(",")
    val station = field(0)
    val max = field(2)
    val temperature = field(3)
    (station, max, temperature)
  }
  
  def main (args: Array[String]){
    Logger.getLogger("org").setLevel(Level.ERROR)
    val sc = new SparkContext("local[*]", "myMaxTemp")
    val lines = sc.textFile("../1800.csv")
    val parsedLines = lines.map(parseLine)
    val maxLines = parsedLines.filter(x => x._2 == "TMAX").map(x => (x._1, x._3.toDouble))
    val result = maxLines.reduceByKey((x, y) => Math.max(x, y)).collect()
    for (each <- result){
      val station = each._1
      val temp = each._2
      println(f"$station max temp is: $temp%.2f F")
    }
  }
}