package junjieSpark

import java.util.Random

import org.apache.log4j.Logger
import org.apache.log4j.Level

import scala.io.Codec
import java.nio.charset.CodingErrorAction

import scala.io.Source
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd._
import org.apache.spark.mllib.recommendation.{ALS, Rating, MatrixFactorizationModel}

object RecommendationALSBig {
  
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
         var prefields = line.split("\",")(0)
         var fields = prefields.split(",\"")
         movieNames += (fields(0).toInt -> (fields(1) + " <" + prefields(1) + ">"))
       }
       else {
         var fields = line.split(',')
         movieNames += (fields(0).toInt -> (fields(1) + " <" + fields(2) + ">"))
       }
     }
    
     return movieNames
  }
  
  def main(args: Array[String]) {
    // Set the log level to only print errors
    Logger.getLogger("org").setLevel(Level.ERROR)
    
    // Create a SparkContext using every core of the local machine
    val sc = new SparkContext("local[*]", "RecommendationsALSBig")
    
    
    // 1 million data
//    val ratings = sc.textFile("../ml-1m/ratings.dat").map { line =>
//      val fields = line.split("::")
//      // format: (timestamp % 10, Rating(userId, movieId, rating))
//      (fields(3).toLong % 10, Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble))
//    }
//
//    val movies = sc.textFile("../ml-1m/movies.dat").map { line =>
//      val fields = line.split("::")
//      // format: (movieId, movieName)
//      (fields(0).toInt, fields(1))
//    }.collect.toMap
    
    // 20 million data
    val ratings = sc.textFile("../ml-20m/ratings.csv").map { line =>
      val fields = line.split(',')
      // format: (timestamp % 10, Rating(userId, movieId, rating))
      (fields(3).toLong % 10, Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble))
    }

    val movies = loadMovieNames()
    
    val totalRatings = ratings.count
    val totalUsers = ratings.map(_._2.user).distinct.count
    val totalMovies = ratings.map(_._2.product).distinct.count

    println("Movie Recommendation System by Junjie Ouyang")
    println("Based on " + totalRatings + " ratings from " + totalMovies + " movies by " + totalUsers + " users.")
    
    
    // Randomly choose 10 movies for the client to rate them.
    // take 100 most rated movies as a pool
    val mostRatedMovies = ratings.map(_._2.product).countByValue.toSeq.sortBy(- _._2).take(100).map(_._1)
                                                
    val random = new Random(0)
    // select about 10 movies out of 100 movie pool
    val randomSelectedMovies = mostRatedMovies.filter(x => random.nextDouble() < 0.1).map(x => (x, movies(x))).toSeq
    
                                          
//    for (each <- randomSelectedMovies) println(each)
    
    val myRatings = customRate(randomSelectedMovies)
    val myRatingsRDD = sc.parallelize(myRatings)

    
    
    //partition training data
    val partition = 20
    val training = ratings.filter(x => x._1 >= 4).values.union(myRatingsRDD).repartition(partition).persist
//    val training = ratings.filter(x => x._1 >= 4).values.repartition(partition).persist
    
    val validation = ratings.filter(x => x._1 >= 2 && x._1 < 4).values.repartition(partition).persist
    
    val test = ratings.filter(x => x._1 < 2).values.persist

    val numTraining = training.count
    val numValidation = validation.count
    val numTest = test.count

    println("Training: " + numTraining + ", validation: " + numValidation + ", test: " + numTest)
    
    
    //Training using ALS
    // for 20m data, the opt is rank = 12 and lambda = 0.1, and numIter = 20
    val ranks = List(8, 12)
    val lambdas = List(0.1)
    val numIters = List(20)
    var optModel: Option[MatrixFactorizationModel] = None
    var optRMSE = Double.MaxValue
    var optRank = 0
    var optLambda = -1.0
    var optNumIter = -1
    for (rank <- ranks; lambda <- lambdas; numIter <- numIters) {
      val model = ALS.train(training, rank, numIter, lambda)
      val RMSE = getRMSE(model, validation, numValidation)
      println("RMSE of validation = " + RMSE + ", rank = " + rank + ", lambda = " + lambda + ", numIteration = " + numIter + ".")
      if (RMSE < optRMSE) {
        optModel = Some(model)
        optRMSE = RMSE
        optRank = rank
        optLambda = lambda
        optNumIter = numIter
      }
    }

    // evaluate the optimal model on the test set (optional)

    val testRMSE = getRMSE(optModel.get, test, numTest)

    println("The best model was trained with rank = " + optRank + " and lambda = " + optLambda
      + ", and numIter = " + optNumIter + ", the test set RMSE is " + testRMSE + ".")

    // compare a naive benchmark with the optimal model

    val meanRating = training.union(validation).map(_.rating).mean
    // a naive benchmark returns RMSE for the average rating.
    val benchMark = math.sqrt(test.map(x => (meanRating - x.rating) * (meanRating - x.rating)).reduce(_ + _) / numTest)
    
    val improvement = (benchMark - testRMSE) / benchMark * 100
    println("The optimal model decreases the RMSE by " + "%.2f".format(improvement) + "% from benchmark.")

    // make 20 recommendations for the client
    val myRatedMovieIds = myRatings.map(_.product).toSet
    val candidates = sc.parallelize(movies.keys.filter(!myRatedMovieIds.contains(_)).toSeq)
    val recommendations = optModel.get.predict(candidates.map((0, _))).collect.sortBy(- _.rating).take(20)

    var i = 1
    println("Movies recommended for you:")
    recommendations.foreach { r =>
      println("%2d".format(i) + ": " + movies(r.product))
      i += 1
    }
    
    sc.stop();
  }
  
  // Compute Root Mean Squared Error
  def getRMSE(model: MatrixFactorizationModel, data: RDD[Rating], n: Long) = {
    val predictions: RDD[Rating] = model.predict(data.map(x => (x.user, x.product)))
    val predictedRatings = predictions.map(x => ((x.user, x.product), x.rating))
    .join(data.map(x => ((x.user, x.product), x.rating))).values
    
    math.sqrt(predictedRatings.map(x => (x._1 - x._2) * (x._1 - x._2)).reduce(_ + _) / n)
  }
  
  // input movie rating by yourself
  def customRate(movies: Seq[(Int, String)]) = {
    val prompt = "What is your rate for the following movie (not seen: 0, worst: 1, best: 5):"
    println(prompt)
    val ratings = movies.flatMap { x =>
      var rating: Option[Rating] = None
      var valid = false
      while (!valid) {
        print(x._2 + ": ")
        try {
          val r = Console.readInt
          if (r >= 0 && r <= 5) {
            valid = true
            if (r > 0) rating = Some(Rating(0, x._1, r))
          }
          else {
            println(prompt)
          }
        } catch {
          case e: Exception => println(prompt)
        }
      }
      rating match {
        case Some(r) => Iterator(r)
        case None => Iterator.empty
      }
    }
    ratings
  }
  
   
}