package com.hgx.hgxaiagent.rag;

import com.hgx.hgxaiagent.advisor.MyLoggerAdvisor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PgVectorVectorStoreConfigTest {


//    @Resource
//    private VectorStore pgVectorVectorStore;

    @Resource(name = "pgVectorVectorStore")
    private VectorStore pgVectorVectorStore;

    @Test
    void pgVectorVectorStore() {
//        List<Document> documents = List.of(new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
//                Map.of("meta1", "meta1")),
//                new Document("The World is Big and Salvation Lurks Around the Corner"),
//                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));
//
//        pgVectorVectorStore.add(documents);

        List<Document> result = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
        Assertions.assertNotNull(result);
    }



    @Test
    void testVectorStore() {
        List<Document> result = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("鹰山公园").topK(2).build());
        System.out.println(result);


    }
}
