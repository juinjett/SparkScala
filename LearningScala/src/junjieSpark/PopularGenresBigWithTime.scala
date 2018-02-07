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
object PopularGenresBigWithTime {
  
  /** Load up a Map of movie IDs to movie names. */
  def loadMovieNames() : Map[Int, String] = {
    
    // Handle character encoding issues:
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Create a Map of Ints to Strings`.
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
  
  /** Load up a Map of movie IDs to movie names. */
  def loadMovieGenres() : Map[Int, String] = {
    
    // Handle character encoding issues:
    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    // Create a Map of Ints to Strings.
    var movieGenres:Map[Int, String] = Map()
    
     val lines = Source.fromFile("../ml-20m/movies.csv").getLines()
     for (line <- lines) {
       if (line.contains("\"")){
         var prefields = line.split("\",")
         var fields = prefields(0).split(",\"")
         movieGenres += (fields(0).toInt -> prefields(1))
       }
       else {
         var fields = line.split("[,\"]+")
         movieGenres += (fields(0).toInt -> fields(2))
       }
     }
    
     return movieGenres
  }
  
  // Case class so we can get a column name for our movie ID
//  final case class Movie(movieID: Int)
  
//  def parseMovieToGenres(s: String, genres: Map[Int, String]) = {
//    var res: Option[(String, Int)] = None
//    val fields = s.split(',')
////    val fields = s.split("\t")
//    val genresOneMovie = genres(fields(0).toInt).split("\\|")
//    for (each <- genresOneMovie){
//      res = Some((each, 1))
//    }
//    res
//  }
  
  /** Our main function where the action happens */
  def main(args: Array[String]) {
   
    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)
    
    // Use new SparkSession interface in Spark 2.0
    val spark = SparkSession
      .builder
      .appName("PopularGenresBig")
      .master("local[*]")
      .getOrCreate()
    
    // Read in each rating line and extract the movie ID; construct an RDD of Movie objects.
    val names = loadMovieNames()
      
    val genresMap = loadMovieGenres()
    
    val lines = spark.sparkContext.textFile("../ml-20m/ratings.csv").map(x => genresMap(x.split(',')(1).toInt))
//    for (each <- lines) println(each)

    
    val genres = lines.flatMap(_.split("\\|")).map((_, 1)).reduceByKey(_+_)
//    for (each <- genres) println(each)
    
    
    val popularGenres = genres.map(x => (x._2, x._1)).sortByKey(false).collect()
    
    val genresInMap = genresMap.values.toSeq
    
    val genresInMapRDD = spark.sparkContext.parallelize(genresInMap).flatMap(_.split("\\|")).map((_, 1)).reduceByKey(_+_)

    val popularGenresInMap = genresInMapRDD.map(x => (x._2, x._1)).sortByKey(false).collect()
    
    // Print the results
    println("Top 10 popular genres that rated")
    var i = 1
    for (each <- popularGenres) {
      val genre = each._2
      val score = each._1
      println (i +". "+ genre + ": " + score)
      i +=1
    }
    
    println("Top 10 popular genres in record")
    var j = 1
    for (each <- popularGenresInMap) {
      val genre = each._2
      val score = each._1
      println (j +". "+ genre + ": " + score)
      j +=1
    }
    // Stop the session
    spark.stop()
  }
  
}

