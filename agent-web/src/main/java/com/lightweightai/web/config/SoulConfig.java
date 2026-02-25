package com.lightweightai.web.config;

import com.lightweightai.assessment.AssessmentRepository;
import com.lightweightai.assessment.AssessmentReportGenerator;
import com.lightweightai.assessment.AssessmentScorer;
import com.lightweightai.assessment.AssessmentService;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.safety.ContentFilter;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.user.EmotionRepository;
import com.lightweightai.user.UserRepository;
import com.lightweightai.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Spring beans for the soul-* modules.
 */
@Configuration
public class SoulConfig {

    private static final Logger logger = LoggerFactory.getLogger(SoulConfig.class);

    @Value("${app.soul.db-path:./data/soul_data.db}")
    private String dbPath;

    @Bean
    public CrisisDetector crisisDetector() {
        return new CrisisDetector();
    }

    @Bean
    public ContentFilter contentFilter() {
        return new ContentFilter();
    }

    @Bean
    public UserRepository userRepository() {
        ensureDbDir();
        return new UserRepository(dbPath);
    }

    @Bean
    public EmotionRepository emotionRepository() {
        ensureDbDir();
        return new EmotionRepository(dbPath);
    }

    @Bean
    public UserService userService(UserRepository userRepository, EmotionRepository emotionRepository) {
        return new UserService(userRepository, emotionRepository);
    }

    @Bean
    public AssessmentRepository assessmentRepository() {
        ensureDbDir();
        return new AssessmentRepository(dbPath);
    }

    @Bean
    public AssessmentReportGenerator assessmentReportGenerator(LLMProvider llmProvider) {
        return new AssessmentReportGenerator(llmProvider);
    }

    @Bean
    public AssessmentScorer assessmentScorer() {
        return new AssessmentScorer();
    }

    @Bean
    public AssessmentService assessmentService(AssessmentScorer scorer,
                                               AssessmentReportGenerator reportGenerator,
                                               AssessmentRepository repository) {
        return new AssessmentService(scorer, reportGenerator, repository);
    }

    private void ensureDbDir() {
        File dir = new File(dbPath).getParentFile();
        if (dir != null && !dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Created soul data directory: {}", dir.getAbsolutePath());
            }
        }
    }
}
