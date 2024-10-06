package com.github.kettoleon.llm.sandbox.chat.repo;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

import static com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils.markdownToHtml;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Chat chat;

    @Column(columnDefinition = "LONGTEXT")
    private String text;

    public String getTextAsHtml() {
        return markdownToHtml(text);
    }

    private ZonedDateTime created;

    private String createdBy;

}
