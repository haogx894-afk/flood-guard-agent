package com.hgx.hgxaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class HaoManusTest {

    @Resource
    private   HaoManus haoManus;

    @Test
    public void run(){
        String userPrompt = """
                北宫镇东河沿村周边存在山洪风险隐患，请基于当前已有的 GIS 数据和防洪预案，
                帮我分析该村所属的行政区、乡镇、山洪沟流域和五大流域，并结合一些网络图片，
                并结合预案内容，生成一份山洪防御空间辅助决策分析报告。
                
                请注意：如果当前数据中未查询到相关对象，请明确说明“当前数据中未查询到”，不要编造。
                最终请以 PDF 格式输出。
                """;
        String answer = haoManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }

}