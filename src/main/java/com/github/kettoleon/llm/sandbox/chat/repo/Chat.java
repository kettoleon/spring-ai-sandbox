package com.github.kettoleon.llm.sandbox.chat.repo;

//TODO decide if this goes into the API or not

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Chat {

    @Id
    private String id;

    private String title;

    @OneToMany(mappedBy = "chat", fetch = FetchType.LAZY, orphanRemoval = true, cascade = {CascadeType.DETACH, CascadeType.REMOVE})
    private List<Message> messages;

    private ZonedDateTime created;


}
