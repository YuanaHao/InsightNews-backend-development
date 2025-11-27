package com.sosd.insightnews.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosd.insightnews.constant.RedisConstants;
import com.sosd.insightnews.converter.NewsConverter;
import com.sosd.insightnews.dao.entity.FavoriteDislike;
import com.sosd.insightnews.dao.entity.NewsDetection;
import com.sosd.insightnews.dao.mapper.FavoriteDislikeMapper;
import com.sosd.insightnews.dao.mapper.NewsDetectionMapper;
import com.sosd.insightnews.domain.AIAnalysisResult;
import com.sosd.insightnews.dto.NewsDTO;
import com.sosd.insightnews.service.AIService;
import com.sosd.insightnews.service.NewsDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 新闻检测服务实现类
 */
@Service
@Slf4j
public class NewsDetectionServiceImpl extends ServiceImpl<NewsDetectionMapper, NewsDetection> implements NewsDetectionService {

    @Autowired
    private AIService aiService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private FavoriteDislikeMapper favoriteDislikeMapper;

    // 用于对象和JSON字符串的转换
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 上传文本新闻进行检测
     */
    @Override
    @Transactional
    public NewsDTO uploadTextNews(String content, String userId) {
        log.info("用户 {} 提交文本检测，长度: {}", userId, content.length());

        // 1. 调用 AI 服务进行分析
        AIAnalysisResult aiResult = aiService.detectText(content);
        // 2. 为了展示美观，顺便生成一个标题
        String title = aiService.generateTitle(content);

        // 3. 构建数据库实体
        NewsDetection entity = new NewsDetection();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setContent(content); // 存入原文
        entity.setSource("User Upload (Text)");
        entity.setNewsType("text");
        entity.setPublishDate(new Date());
        entity.setCreationTime(new Date());

        // 4. 填充 AI 分析结果
        fillAnalysisData(entity, aiResult, "text");

        // 5. 保存到数据库
        save(entity);

        // 6. 记录到 Redis 用户历史 (ZSet: key=userId, value=newsId, score=timestamp)
        cacheUserHistory(userId, entity.getId());

        // 7. 返回前端需要的 DTO
        return NewsConverter.NewsDetectionToNewsDTO(entity);
    }

    /**
     * 上传文件(图片)新闻进行检测
     * filePath: 通常是 OSS 的 URL
     */
    @Override
    @Transactional
    public NewsDTO uploadFileNews(String filePath, String userId) {
        log.info("用户 {} 提交图片检测，URL: {}", userId, filePath);

        // 1. 调用 AI 服务进行图片取证分析
        AIAnalysisResult aiResult = aiService.detectImage(filePath);
        
        // 2. 生成一个简短的描述作为标题
        String title = "图片检测：" + (aiResult.getSummary().length() > 10 ? aiResult.getSummary().substring(0, 10) + "..." : aiResult.getSummary());

        // 3. 构建数据库实体
        NewsDetection entity = new NewsDetection();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setUrl(filePath); // 存入图片链接
        entity.setSource("User Upload (Image)");
        entity.setNewsType("image");
        entity.setPublishDate(new Date());
        entity.setCreationTime(new Date());

        // 4. 填充 AI 分析结果
        fillAnalysisData(entity, aiResult, "image");

        // 5. 保存到数据库
        save(entity);

        // 6. 记录到 Redis 用户历史
        cacheUserHistory(userId, entity.getId());

        return NewsConverter.NewsDetectionToNewsDTO(entity);
    }

    /**
     * 多模态检测
     */
    @Override
    @Transactional
    public NewsDTO uploadMultimodalNews(String imageUrl, String content, String userId) {
        log.info("用户 {} 提交多模态检测，URL: {}, 文本长度: {}", userId, imageUrl, content.length());

        // 1. 调用 AI 进行图文一致性分析
        AIAnalysisResult aiResult = aiService.detectMultimodal(imageUrl, content);

        // 2. 生成标题 (取文本摘要或生成新标题)
        String title = "图文核查：" + (content.length() > 10 ? content.substring(0, 10) + "..." : content);

        // 3. 构建实体
        NewsDetection entity = new NewsDetection();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setUrl(imageUrl);   // 存图片
        entity.setContent(content); // 存文本
        entity.setSource("User Upload (Multimodal)");
        entity.setNewsType("multimodal");
        entity.setPublishDate(new Date());
        entity.setCreationTime(new Date());

        // 4. 填充结果
        String analysisText = "【图文一致性判定】\n" + (aiResult.getIsConsistent() ? "一致" : "不一致") + 
                              "\n\n【总结】\n" + aiResult.getSummary() + 
                              "\n\n【详细分析】\n" + aiResult.getAnalysis();
        
        entity.setCredibility(aiResult.getCredibility());
        entity.setResponseText(analysisText);

        // 序列化图片证据链 (bbox)
        try {
            if (aiResult.getImageEvidenceChain() != null) {
                entity.setEvidenceChain(objectMapper.writeValueAsString(aiResult.getImageEvidenceChain()));
            } else {
                entity.setEvidenceChain("[]");
            }
        } catch (JsonProcessingException e) {
            log.error("证据链序列化失败", e);
            entity.setEvidenceChain("[]");
        }

        save(entity);
        cacheUserHistory(userId, entity.getId());
        return NewsConverter.NewsDetectionToNewsDTO(entity);
    }

    /**
     * 辅助方法：将 AI 结果填充到实体中
     */
    private void fillAnalysisData(NewsDetection entity, AIAnalysisResult result, String type) {
        // 设置可信度
        entity.setCredibility(result.getCredibility());
        // 设置分析文本 (Summary + Analysis)
        entity.setResponseText("【总结】\n" + result.getSummary() + "\n\n【详细分析】\n" + result.getAnalysis());
        
        // 序列化证据链为 JSON 字符串存储
        try {
            if ("text".equals(type) && result.getTextEvidenceChain() != null) {
                entity.setEvidenceChain(objectMapper.writeValueAsString(result.getTextEvidenceChain()));
            } else if ("image".equals(type) && result.getImageEvidenceChain() != null) {
                // 图片模式：存储包含 bbox 的证据链
                entity.setEvidenceChain(objectMapper.writeValueAsString(result.getImageEvidenceChain()));
            } else {
                entity.setEvidenceChain("[]");
            }
        } catch (JsonProcessingException e) {
            log.error("证据链序列化失败", e);
            entity.setEvidenceChain("[]");
        }
    }

    /**
     * 辅助方法：缓存用户历史记录
     */
    private void cacheUserHistory(String userId, Long newsId) {
        String key = "history:detection:" + userId;
        // 使用当前时间戳作为 score，保证按时间排序
        stringRedisTemplate.opsForZSet().add(key, String.valueOf(newsId), System.currentTimeMillis());
        // 设置过期时间 (例如 30 天)
        stringRedisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    @Override
    public NewsDTO viewAnalysisReport(Long newsId) {
        NewsDetection news = getById(newsId);
        if (news == null) {
            return null;
        }
        return NewsConverter.NewsDetectionToNewsDTO(news);
    }

    @Override
    public List<NewsDTO> getHistoryDetections(String userId) {
        String key = "history:detection:" + userId;
        
        // 1. 从 Redis 获取最新的 20 条记录 (按分数倒序)
        Set<String> newsIds = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 19);
        
        if (newsIds == null || newsIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 批量查询数据库
        List<Long> ids = newsIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<NewsDetection> newsList = listByIds(ids);

        // 3. 转换为 DTO 并按时间倒序排列 (数据库批量查询不保证顺序，需重新排序)
        return newsList.stream()
                .sorted(Comparator.comparing(NewsDetection::getCreationTime).reversed())
                .map(NewsConverter::NewsDetectionToNewsDTO)
                .collect(Collectors.toList());
    }

    // --- 点赞/收藏/点踩 逻辑 (包含数据库持久化) ---

    @Override
    @Transactional
    public void favoriteNews(Long newsId, String userId) {
        // 1. 操作数据库
        handleDatabaseInteraction(newsId, userId, "favorite");
        
        // 2. 同步 Redis Set (用于 isCollect 状态)
        String key = RedisConstants.NEWS_FAVORITE_KEY + newsId;
        toggleSet(key, userId);
        
        // 3. 同步 Redis Count (用于 favoriteCount)
        String countKey = RedisConstants.NEWS_FAVORITE_COUNT_KEY + newsId;
        updateRedisCount(countKey, key, userId);
    }

    @Override
    @Transactional
    public void dislikeNews(Long newsId, String userId) {
        // 1. 操作数据库
        handleDatabaseInteraction(newsId, userId, "dislike");
        
        // 2. 同步 Redis Set
        String key = RedisConstants.NEWS_DISLIKE_KEY + newsId;
        toggleSet(key, userId);
    }

    /**
     * 数据库通用操作：有则删（取消），无则增（添加）
     */
    private void handleDatabaseInteraction(Long newsId, String userId, String operationType) {
        LambdaQueryWrapper<FavoriteDislike> query = new LambdaQueryWrapper<>();
        query.eq(FavoriteDislike::getOperatorId, userId)
             .eq(FavoriteDislike::getTargetId, String.valueOf(newsId))
             .eq(FavoriteDislike::getTargetType, "news")
             .eq(FavoriteDislike::getOperationType, operationType);
        
        FavoriteDislike exists = favoriteDislikeMapper.selectOne(query);
        if (exists != null) {
            favoriteDislikeMapper.deleteById(exists.getId());
        } else {
            FavoriteDislike fd = new FavoriteDislike();
            fd.setOperatorId(userId);
            fd.setTargetId(String.valueOf(newsId));
            fd.setTargetType("news");
            fd.setOperationType(operationType);
            fd.setOperationTime(new Date());
            favoriteDislikeMapper.insert(fd);
        }
    }

    private void toggleSet(String key, String value) {
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value))) {
            stringRedisTemplate.opsForSet().remove(key, value);
        } else {
            stringRedisTemplate.opsForSet().add(key, value);
        }
    }
    
    private void updateRedisCount(String countKey, String setKey, String userId) {
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(setKey, userId))) {
            stringRedisTemplate.opsForValue().increment(countKey);
        } else {
            stringRedisTemplate.opsForValue().decrement(countKey);
        }
    }

    @Override
    public String downloadAnalysisReport(Long newsId, String format) {
        return "报告生成功能开发中... (ID: " + newsId + ")";
    }
}