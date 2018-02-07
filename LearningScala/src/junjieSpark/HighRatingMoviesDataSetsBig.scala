package junjieSpark

import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.sql._
import org.apache.log4j._
import scala.io.Source
import java.nio.charset.CodingErrorAction
import scala.io.Codec
import org.apache.spark.sql.functions._

/** Find the movies with the most ratings. */
object HighRatingMoviesDataSetsBig {
  
  /** Load up a Map of movie IDs to movie names. */
  def loadMovieNames() : Map[Int, String] = {
    
    // Handle character encoding issues:
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Create a Map of Ints to Strings, and populate it from u.item.
    var movieNames:Map[Int, String] = Map()
    
     val lines = Source.fromFile("../ml-20m/movies.csv").getLines()
     for (line <- lines) {
       if (line.contains("\"")){
         var prefields = line.split("\",")
         var fields = prefields(0).split(",\"")
         movieNames += (fields(0).toInt -> fields(1))
       }
       else {
         var fields = line.split(',')
         movieNames += (fields(0).toInt -> fields(1))
       }
     }
    
     return movieNames
  }
 
//  // Case class so we can get a column name for our movie ID
//  final case class Movie(movieID: Int, rating: Double)
  
  def parseMovie(s: String) = {
    val fields = s.split(',')
//    val fields = s.split("\t")
    (fields(1).toInt, (fields(2).toDouble, 1))
  }
  
  /** Our main function where the action happens */
  def main(args: Array[String]) {
   
    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)
    
    // Use new SparkSession interface in Spark 2.0
    val spark = SparkSession
      .builder
      .appName("HighRatingMovies")
      .master("local[*]")
//      .config("spark.sql.warehouse.dir", "file:///C:/temp") // Necessary to work around a Windows bug in Spark 2.0.0; omit if you're not on Windows.
      .getOrCreate()
    
    // Read in each rating line and extract the movie ID; construct an RDD of Movie objects.
    val lines = spark.sparkContext.textFile("../ml-20m/ratings.csv").map(parseMovie)
//    val lines = spark.sparkContext.textFile("../ml-100k/u.data").map(parseMovie)
    
    // Count movies and choose popular movies (more than 20000 rates)
    val movieCount = lines.reduceByKey((x,y) => (x._1+y._1, x._2+y._2)).filter(x => x._2._2 > 20000)
    
    // Calculate the average rate
    val movieAvgRate = movieCount.map(x => (x._1, x._2._1/x._2._2, x._2._2))
    
    // Sort by the rate score, take 10 highest movies
    val result = movieAvgRate.map(x => (x._2, (x._1, x._3))).sortByKey(false).collect().take(10)
    
    val names = loadMovieNames()
    
    // Print the results
    println("Top 10 movies")
    for (each <- result) {
      val title = names(each._2._1.asInstanceOf[Int])
      val score = each._1
      val times = each._2._2
      println (title + ": " + score + ": " + times)
    }
    
    spark.stop()
  }
  
}

