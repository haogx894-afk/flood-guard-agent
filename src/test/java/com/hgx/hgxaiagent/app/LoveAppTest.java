package com.hgx.hgxaiagent.app;

import com.hgx.hgxaiagent.advisor.MyLoggerAdvisor;
import com.hgx.hgxaiagent.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void testChat() {
        //获取对话id
        String chatId = UUID.randomUUID().toString();
        //开始对轮对话
        //第一轮
        String message = "你好我是hgx";
        String answer = loveApp.doChat(chatId, message);
        //第二轮
        message = "我想让另一半（编程导航）更爱我";
        answer = loveApp.doChat(chatId, message);
        Assertions.assertNotNull(answer);
        //第三轮
        message = "我叫什么名字？";
        answer = loveApp.doChat(chatId, message);
        Assertions.assertNotNull(answer);


    }

    @Test
    void doChatWithReport() {

        String chatId = UUID.randomUUID().toString();
        String message = "你好，我想知道张家坟村的防御领导组的负责人是谁";
        LoveApp.LoveReport loveReport = loveApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "北宫镇东河沿村的防御领导组的负责人的电话是啥";
        String answer = loveApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRagWithRewriter() {
        String chatId = UUID.randomUUID().toString();
        String message = "老子是hgx，我就想问你一个问题，你服不服，东河沿村有哪些测站";
        String answer = loveApp.doChatWithRagWithRewriter(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {

        // 测试联网搜索问题的答案
        testMessage("在北京的地方，推荐几个好玩的水库");

        // 测试网页抓取：恋爱案例分析
//        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        // 测试资源下载：图片下载
//        testMessage("从网上，直接下载一张普通水库的照片");

        // 测试终端操作：执行代码
//        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
//        testMessage("保存北京市山洪辅助智能体的功能为文件");

        // 测试 PDF 生成
        testMessage("生成一份“北京市山洪辅助智能体的功能为”PDF");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

//    @Test
//    void doChatWithMcpStdio() {
//        String chatId = UUID.randomUUID().toString();
//        String message = "帮我搜索一些水库  的图片";
//        String answer = loveApp.doChatWithMcp(message, chatId);
//        Assertions.assertNotNull(answer);
//
//    }

//    @Test
//    void doChatWithMcp() {
//       String chatId = UUID.randomUUID().toString();
//       String message = "我的另一半在内蒙古赤峰市锦山镇，给我推荐五公里周围适合约会的地方";
//       String answer = loveApp.doChatWithMcp(message, chatId);
//       Assertions.assertNotNull(answer);
//    }



}

//    //测试向量数据库能不能搜到内容，测试结果：在向量数据库中能搜到内容
//    @Resource()
//    private VectorStore loveAppVectorStore;
//
//    @Test
//    void searchRag() {
//        List<Document> documents = loveAppVectorStore.similaritySearch(
//                SearchRequest.builder()
//                        .query("张家坟村 防御领导组 负责人")
//                        .topK(10)
//                        .similarityThresholdAll()
//                        .build()
//        );
//
//        for (Document document : documents) {
//            System.out.println("score = " + document.getScore());
//            System.out.println("metadata = " + document.getMetadata());
//            System.out.println("text = " + document.getText());
//            System.out.println("===============");
//        }
//    }


