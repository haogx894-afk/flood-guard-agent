package com.hgx.hgxaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring Ai框架来调用ai
 */
//@Component
public class SpringAiAiinvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        ChatResponse response = dashscopeChatModel.call(new Prompt("你好，我是一名学习ai应用开发的学生,你具体是什么模型，调用的哪个模型"));
        System.out.println(response);
    }
}
