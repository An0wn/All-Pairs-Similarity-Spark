package edu.ucsb.apss.PSS

import edu.ucsb.apss.InvertedIndex.{SimpleInvertedIndex, FeaturePair}
import org.apache.spark.mllib.linalg.SparseVector

/**
  * Created by dimberman on 4/28/16.
  */
object SimilarityCalculator extends Serializable {
    def calculateScores(vec:SparseVector, invertedIndex: Map[Int, List[FeaturePair]], indexMap:Map[Long,Int], score:Array[Double]) = {
        vec.indices.zipWithIndex.foreach {
            case (featureIndex, j) =>
                if (invertedIndex.contains(featureIndex)) {
                    val weight_j = vec.values(j)
                    invertedIndex(featureIndex).foreach {
                        case (featurePair) => {
                            val (ind_i, weight_i) = (featurePair.id, featurePair.weight)
                            val l = indexMap(ind_i)
                            score(l) += weight_i * weight_j
                        }
                    }
                }
        }
    }

    def calculateInvIndScores(vec:SimpleInvertedIndex, invertedIndex: Map[Int, List[FeaturePair]], indexMap:Map[Long,Int], vecIndexMap:Map[Long,Int], score:Array[Array[Double]]) = {




        vec.indices.keys.foreach{
            l => {
                if(invertedIndex.contains(l)){
                    for(FeaturePair(j, weight_j) <- vec.indices(l)){
                        for(FeaturePair(i, weight_i) <- invertedIndex(l)){
                            val ind_i =  indexMap(i)
                            val ind_j = vecIndexMap(j)
                            score(ind_i)(ind_j) += weight_i * weight_j
                        }
                    }
                }
            }
        }


//
//        val mutalFeatures = indices.keySet.intersect(invertedIndex.keySet)
//
//
//        mutalFeatures.foreach(
//            i =>{
//                val inner = invertedIndex(i)
//                val outer = indices(i)
//                for(FeaturePair(i, weight_i) <- inner){
//                    for(FeaturePair(j, weight_j) <- outer){
//                        score(indexMap(i))(vecIndexMap(j)) += weight_i * weight_j
//                    }
//                }
//            }
//
//        )

    }

    def clearScoreArray(scores:Array[Double]) = {
        for (l <- scores.indices) {
                scores(l) = 0
        }
    }

    def clearInvIndArray(scores:Array[Array[Double]]) = {
        for (l <- scores.indices) {
            for(k <- scores(l).indices)
            scores(l)(k) = 0
        }
    }
}
