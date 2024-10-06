package com.github.kettoleon.llm.sandbox.chat.repo;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepositoryImplementation<Message, String> {

    List<Message> findAllByChatOrderByCreated(Chat chat);
}
