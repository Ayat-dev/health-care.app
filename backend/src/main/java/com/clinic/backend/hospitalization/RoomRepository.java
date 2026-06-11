package com.clinic.backend.hospitalization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.department ORDER BY r.roomNumber ASC")
    List<Room> findAllWithDepartment();

    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.department WHERE r.active = true ORDER BY r.roomNumber ASC")
    List<Room> findActiveWithDepartment();

    @Query("SELECT r FROM Room r LEFT JOIN FETCH r.department WHERE r.id = :id")
    Optional<Room> findWithDepartmentById(Long id);

    boolean existsByRoomNumberIgnoreCase(String roomNumber);

    Optional<Room> findByRoomNumberIgnoreCase(String roomNumber);
}
