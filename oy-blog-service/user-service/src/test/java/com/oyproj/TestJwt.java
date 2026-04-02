package com.oyproj;

import com.oyproj.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestJwt {

    @Test
    public void testJwt(){
        String token = JwtUtil.generateAccessToken("a");
        Claims claims = JwtUtil.parseToken(token);
        System.out.println(claims.getSubject());
    }

}
