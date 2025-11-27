package com.sosd.insightnews.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosd.insightnews.constant.RedisConstants;
import com.sosd.insightnews.converter.NewsConverter;
import com.sosd.insightnews.dao.entity.NewsDetection;
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

    // --- 以下是点赞/点踩/下载报告的简单实现 ---

    @Override
    public void favoriteNews(Long newsId, String userId) {
        String key = RedisConstants.NEWS_FAVORITE_KEY + newsId;
        toggleSet(key, userId);
    }

    @Override
    public void dislikeNews(Long newsId, String userId) {
        String key = RedisConstants.NEWS_DISLIKE_KEY + newsId;
        toggleSet(key, userId);
    }

    private void toggleSet(String key, String value) {
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value))) {
            stringRedisTemplate.opsForSet().remove(key, value);
        } else {
            stringRedisTemplate.opsForSet().add(key, value);
        }
    }

    @Override
    public String downloadAnalysisReport(Long newsId, String format) {
        // TODO: 集成 PDF 生成工具 (如 iText 或 wkhtmltopdf)
        // 目前先返回一个简单的提示或 URL
        return "报告生成功能开发中... (ID: " + newsId + ")";
    }
}