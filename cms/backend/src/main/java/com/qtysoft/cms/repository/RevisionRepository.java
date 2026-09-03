package com.qtysoft.cms.repository;

import com.qtysoft.cms.model.Revision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RevisionRepository extends JpaRepository<Revision, Long> {
    List<Revision> findByDocKeyOrderByCreatedAtDesc(String docKey);

    @Query("select r from Revision r where r.docKey = :key order by r.createdAt desc")
    List<Revision> history(@Param("key") String key);
}
