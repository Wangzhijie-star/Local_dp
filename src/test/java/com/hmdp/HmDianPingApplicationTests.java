package com.hmdp;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;




@SpringBootTest
public class HmDianPingApplicationTests {
    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate StringRedisTemplate;
    @Test
    public void addGeo() {
        //先查询shop信息
        List<Shop> shops=shopService.list();
        //将shop根据shoptype分组，封装到map中
        Map<Long,List<Shop>> map=shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for(Map.Entry<Long,List<Shop>> entry:map.entrySet()){
            Long typeId=entry.getKey();
            List<Shop> value=entry.getValue();
            String key="shop:Geo"+typeId;
            //构造每一个shoptype下的shop，封装成一个geo对象
            List<RedisGeoCommands.GeoLocation<String>> geoList=new ArrayList<>(value.size());
            for(Shop shop:value){
                geoList.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            StringRedisTemplate.opsForGeo().add(key,geoList);
        }
    }
}
