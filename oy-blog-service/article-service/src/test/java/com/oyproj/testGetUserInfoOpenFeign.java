package com.oyproj;

import com.oyproj.api.file.client.FileUploadClient;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class testGetUserInfoOpenFeign {
    @Autowired
    private UserClient userClient;
    @Test
    public void test(){
        Result<UserDTO> userDTO = userClient.getUserDTO("2038824018961887237");
        System.out.println(userDTO.getData());
    }
}
