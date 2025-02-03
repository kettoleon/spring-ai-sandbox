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
public class JavaClass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    private JavaFile file;

    @Column(columnDefinition = "LONGTEXT")
    private String qualifiedName;

    @Column(columnDefinition = "LONGTEXT")
    private String functionality;

    @OneToMany(mappedBy = "javaClass", fetch = FetchType.LAZY, orphanRemoval = true, cascade = {CascadeType.DETACH, CascadeType.REMOVE})
    private List<JavaMethod> methods;


}
