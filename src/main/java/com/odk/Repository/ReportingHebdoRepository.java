package com.odk.Repository;

import com.odk.Entity.Activite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReportingHebdoRepository extends JpaRepository<Activite, Long> {

    @Query("SELECT a FROM Activite a WHERE a.dateDebut BETWEEN :start AND :end")
    List<Activite> findByWeek(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
