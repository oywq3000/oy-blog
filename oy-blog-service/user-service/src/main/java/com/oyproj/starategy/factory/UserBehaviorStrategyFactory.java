package com.oyproj.starategy.factory;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.exception.UnsupportedUserTypeException;
import com.oyproj.starategy.UserBehaviorStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class UserBehaviorStrategyFactory {
    
    private final Map<BlogRole, UserBehaviorStrategy> strategies;
    
    @Autowired
    public UserBehaviorStrategyFactory(List<UserBehaviorStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                    UserBehaviorStrategy::supports, 
                    Function.identity()
                ));
    }
    
    public UserBehaviorStrategy getStrategy(BlogRole userType) {
        UserBehaviorStrategy strategy = strategies.get(userType);
        if (strategy == null) {
            throw new UnsupportedUserTypeException();
        }
        return strategy;
    }
}
