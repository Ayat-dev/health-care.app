package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** One section of the bed board: a department and its rooms with live occupancy. */
@Getter @Setter
public class BedBoardDepartmentDto {

    private Long departmentId;
    private String departmentName;
    private String color;
    private List<RoomDto> rooms = new ArrayList<>();

    public int getTotalBeds() {
        return rooms.stream().mapToInt(RoomDto::getCapacity).sum();
    }

    public int getOccupiedBeds() {
        return rooms.stream().mapToInt(RoomDto::getOccupiedCount).sum();
    }

    public int getFreeBeds() {
        return getTotalBeds() - getOccupiedBeds();
    }
}
