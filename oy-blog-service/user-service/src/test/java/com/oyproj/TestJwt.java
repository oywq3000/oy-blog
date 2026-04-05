package com.oyproj;

import com.oyproj.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

public class TestJwt {

    @Test
    public void testJwt(){
        int[] arr = new int[]{5,2,1,9,0};
        quickSort(arr,0,arr.length-1,2);
        for(int i = 0;i<arr.length;i++){
        }
    }
    public void quickSort(int[] array,int low,int high,int k){
        if(low<high){
            int p =partition(array,low,high);
            if(p==k-1){
                return;
            }else if(p<k-1) {
                quickSort(array, low, p - 1, k);
                quickSort(array, p + 1, high, k);
            }
        }
    }
    public int partition(int[] array,int low,int high){
        int pivot = array[low];
        while(low<high){
            while (low<high&&array[high]>=pivot){
                high--;
            }
            array[low] = array[high];
            while (low<high&&array[low]<=pivot){
                low++;
            }
            array[high] = array[low];
        }
        array[low] = pivot;
        return low;
    }

}
