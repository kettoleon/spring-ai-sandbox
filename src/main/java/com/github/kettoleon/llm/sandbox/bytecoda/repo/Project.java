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
public class Project {

    @Id
    private String id;

    private String name;

    private String path;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY, orphanRemoval = true, cascade = {CascadeType.DETACH, CascadeType.REMOVE})
    private List<JavaFile> javaFiles;

}
