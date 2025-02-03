package com.github.kettoleon.llm.sandbox.bytecoda.repo;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JavaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Project project;

    @Column(columnDefinition = "LONGTEXT")
    private String path;

    @OneToMany(mappedBy = "file", fetch = FetchType.LAZY, orphanRemoval = true, cascade = {CascadeType.DETACH, CascadeType.REMOVE})
    private List<JavaClass> classes;


}
