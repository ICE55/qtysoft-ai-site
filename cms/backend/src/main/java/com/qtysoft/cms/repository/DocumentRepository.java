package com.qtysoft.cms.repository;

import com.qtysoft.cms.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByDocKey(String docKey);
}
