package com.sosd.insightnews.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosd.insightnews.constant.RedisConstants;
import com.sosd.insightnews.context.UserContext;
import com.sosd.insightnews.dao.entity.NewsDetection;
import com.sosd.insightnews.dto.NewsDTO;
import com.sosd.insightnews.util.TimeUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;

@Component
public class NewsConverter {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static StringRedisTemplate staticRedisTemplate;
    
    // 引入 ObjectMapper 用于解析 JSON 字符串
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        staticRedisTemplate = this.stringRedisTemplate;
    }

    public static NewsDTO NewsDetectionToNewsDTO(NewsDetection newsDetection) {
        NewsDTO newsDTO = new NewsDTO();
        BeanUtils.copyProperties(newsDetection, newsDTO);
        
        // 1. 手动转换时间格式 (修复 publishDate 为 null 的问题)
        if (newsDetection.getPublishDate() != null) {
            newsDTO.setPublishDate(TimeUtil.df.format(newsDetection.getPublishDate()));
        }
        if (newsDetection.getCreationTime() != null) {
            newsDTO.setCreationTime(TimeUtil.df.format(newsDetection.getCreationTime()));
        }

        // 2. 解析证据链字符串为 JSON 对象 (让前端拿到数组而不是字符串)
        String evidenceJson = newsDetection.getEvidenceChain();
        if (evidenceJson != null && !evidenceJson.isEmpty()) {
            try {
                // 将字符串解析为 JsonNode 或 List/Map，这样 SpringMVC 序列化时就会变成 JSON 数组
                newsDTO.setEvidenceChain(objectMapper.readTree(evidenceJson));
            } catch (JsonProcessingException e) {
                // 如果解析失败，返回空列表或原字符串
                newsDTO.setEvidenceChain(Collections.emptyList());
            }
        } else {
            newsDTO.setEvidenceChain(Collections.emptyList());
        }

        // 3. 填充 Redis 交互数据 (点赞、收藏等)
        String userId = (UserContext.getCurrentUser() != null) ? UserContext.getCurrentUser().getId() : "";
        Long newsId = newsDetection.getId();

        if (staticRedisTemplate != null && !userId.isEmpty()) {
            // 获取用户点赞状态
            Boolean isLiked = staticRedisTemplate.opsForSet().isMember(RedisConstants.NEWS_LIKE_KEY + newsId, userId);
            // 获取用户点踩状态
            Boolean isDisliked = staticRedisTemplate.opsForSet().isMember(RedisConstants.NEWS_DISLIKE_KEY + newsId, userId);
            // 获取用户收藏状态
            Boolean isFavorite = staticRedisTemplate.opsForSet().isMember(RedisConstants.NEWS_FAVORITE_KEY + newsId, userId);

            // 设置点赞和点踩状态
            if (Boolean.TRUE.equals(isLiked)) {
                newsDTO.setIsDislike(1);
            } else if (Boolean.TRUE.equals(isDisliked)) {
                newsDTO.setIsDislike(2);
            } else {
                newsDTO.setIsDislike(0);
            }

            // 设置收藏状态
            newsDTO.setCollect(Boolean.TRUE.equals(isFavorite));

            // 获取点赞数和收藏数
            String likeCount = staticRedisTemplate.opsForValue().get(RedisConstants.NEWS_LIKE_COUNT_KEY + newsId);
            String favoriteCount = staticRedisTemplate.opsForValue().get(RedisConstants.NEWS_FAVORITE_COUNT_KEY + newsId);
            newsDTO.setLikeCount(likeCount != null ? Integer.parseInt(likeCount) : 0);
            newsDTO.setFavoriteCount(favoriteCount != null ? Integer.parseInt(favoriteCount) : 0);
        } else {
            newsDTO.setIsDislike(0);
            newsDTO.setCollect(false);
            newsDTO.setLikeCount(0);
            newsDTO.setFavoriteCount(0);
        }

        return newsDTO;
    }
}