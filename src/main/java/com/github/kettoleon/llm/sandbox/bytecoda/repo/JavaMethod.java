package com.github.kettoleon.llm.sandbox.bytecoda.repo;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JavaMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    private JavaClass javaClass;

    @Column(columnDefinition = "LONGTEXT")
    private String signature;

    @Column(columnDefinition = "LONGTEXT")
    private String functionality;

    @Column(columnDefinition = "LONGTEXT")
    private String code;

}
