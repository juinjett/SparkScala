package com.sundogsoftware.spark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.log4j._

/** Find the superhero with the most co-appearances. */
object LeastPopularSuperhero {
  
  // Function to extract the hero ID and number of connections from each line
  def count (line: String) = {
    val fields = line.split("\\s+")
    (fields(0).toInt, fields.length-1)
  }
  
  // Function to extract hero ID -> hero name tuples (or None in case of failure)
  def parseNames (line: String): Option[(Int, String)] = {
    val fields = line.split("\"")
    if (fields.length > 1) {
      return Some((fields(0).trim().toInt, fields(1)))
    } else {
      return None
    }
  }
 
  /** Our main function where the action happens */
  def main(args: Array[String]) {
    Logger.getLogger("org").setLevel(Level.ERROR)
    val sc = new SparkContext("local[*]", "LeastPopularSuperhero")
    val names_raw = sc.textFile("../marvel-names.txt")
    val graph_raw = sc.textFile("../marvel-graph.txt")
    val names = names_raw.flatMap(parseNames)
    val graph_line = graph_raw.map(count).reduceByKey( (x,y) => x+y )
    
    val flip_graph_line = graph_line.map(x => (x._2, x._1))
    val leastPopular10 = flip_graph_line.sortByKey().take(10)
    val mostPopular10 = flip_graph_line.sortByKey(false).take(10)
    val result = mostPopular10.map(x => (names.lookup(x._2)(0), x._1))
    result.foreach(println)
//    flip_graph_line.foreach(println)
  }
  
}
