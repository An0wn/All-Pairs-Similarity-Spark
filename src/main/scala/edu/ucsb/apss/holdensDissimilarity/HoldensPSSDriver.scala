package edu.ucsb.apss.holdensDissimilarity

import edu.ucsb.apss.InvertedIndex.InvertedIndex._
import edu.ucsb.apss.InvertedIndex.InvertedIndex
import edu.ucsb.apss.partitioning.HoldensPartitioner
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.distributed.{CoordinateMatrix, MatrixEntry}
import org.apache.spark.rdd.RDD

import scala.collection.mutable.ListBuffer

/**
  * Created by dimberman on 1/3/16.
  */
class HoldensPSSDriver {



    def run(sc: SparkContext, vectors: RDD[SparseVector], numBuckets: Int, threshold: Double) = {
        val count = vectors.count
        val partitioner = new HoldensPartitioner

        val partitionedVectors = partitioner.partitionByL1Norm(vectors, numBuckets, count).persist()

        val bucketLeaders = partitioner.determineBucketLeaders(partitionedVectors).collect()

        //TODO should I modify this so that it uses immutable objects?
        partitioner.tieVectorsToHighestBuckets(partitionedVectors, bucketLeaders, threshold, sc)

        val invIndexes = partitionedVectors.map { case (a, v) => (a, createFeaturePairs(a, v)) }
          //TODO it would be more efficient to not create a new object for every add, otherwise I'm just basically doing a reduce
          .aggregateByKey(InvertedIndex())(
            addInvertedIndexes,
            mergeInvertedIndexes
        ).map { case (x, b) => (x, (b, x)) }


        val assignments = partitioner.createPartitioningAssignments(numBuckets)

        //TODO test that this will guarantee that all key values will be placed into a single partition
        //TODO this function would be the perfect point to filter the values via static partitioning
        val partitionedTasks = partitioner.prepareTasksForParallelization(partitionedVectors, assignments).groupByKey().join(invIndexes)


        val a = partitionedTasks.mapValues {
            case (externalVectors, (invIndx, bucketID)) =>
                val invertedIndex = invIndx.indices
                externalVectors.map {
                    case (buck, v) =>
                        val scores = Array[Double](externalVectors.size)
                        var (r_j, vec) = (v.l1, v.vector)
                        val d_i = invertedIndex.filter(a => vec.indices.contains(a._1))
                        val d_j = vec.indices.flatMap(ind => if (d_i.contains(ind)) Some((ind, vec.values(ind))) else None)

                        d_j.foreach {
                            case (ind_j, weight_j) =>
                                d_i(ind_j).foreach {
                                    case (featurePair) => {
                                        val (ind_i, weight_i) = (featurePair.id, featurePair.weight)
                                        if (!(scores(ind_i) + v.lInf * r_j < threshold)) scores(ind_j) += weight_i * weight_j
                                    }
                                }
                                r_j -= weight_j
                        }
                        scores.zipWithIndex.filter(_._1>threshold).map{case(score, ind_i) => (ind_i, buck, score)}
                }
        }





        //        partitionedTasks.mapPartitions(
        //            iter => {
        //                var a = 0
        //                val b = iter.toStream
        //                b.flatMap(
        //                    summarizedRow => {
        //                        val row = summarizedRow.
        //                        val buf = new ListBuffer[((Int, Int), Double)]()
        //                        b.drop(a+1).foreach(
        //                            summarizedRow2 => {
        //                                val row2 = summarizedRow2.row
        //                                if(summarizedRow.summary.tmax < summarizedRow2.summary.colSum){
        //                                    val c = ((summarizedRow.summary.index, summarizedRow2.summary.index), calculateCosineSimilarity(row, row2))
        //                                    buf += c
        //                                }
        //                            }
        //                        )
        //                        a+=1
        //                        buf
        //                    }
        //                ).toIterator
        //            }
        //
        //        )
        //        val c = sims.collect()
        //        println("sadg")
        //        //        val rSim = sims.reduceByKey(_ + _)
        //        val mSim = sims.map { case ((i, j), sim) =>
        //            MatrixEntry(i.toLong, j.toLong, sim)
        //        }
        //        new CoordinateMatrix(mSim, n, n)
        //    }
    }
}
