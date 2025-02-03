package com.github.kettoleon.llm.sandbox.bytecoda.repo;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JavaMethodRepository extends JpaRepositoryImplementation<JavaMethod, String> {

    List<JavaMethod> findAllByJavaClass(JavaClass javaClass);

    Optional<JavaMethod> findByJavaClassAndSignature(JavaClass javaClass, String signature);

}
