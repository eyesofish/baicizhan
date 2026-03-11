package com.example.demo.learning;

import com.example.demo.learning.dto.FeatureVector;
import com.example.demo.learning.dto.RankedTerm;
import java.util.List;

public interface RankingService {
    List<RankedTerm> rank(List<FeatureVector> featureVectors);
}
