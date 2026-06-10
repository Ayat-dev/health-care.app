package com.clinic.backend.lab;

import com.clinic.backend.catalog.LabTestCatalog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One analysis line within a {@link LabRequest}, pointing at a catalogue entry.
 * Its {@link LabResult} (if any) holds the value entered by the laborantin.
 */
@Entity
@Table(name = "lab_request_items")
@Getter @Setter @NoArgsConstructor
public class LabRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_request_id", nullable = false)
    private LabRequest labRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private LabTestCatalog test;

    @Column(nullable = false, length = 20)
    private String status = "EN_ATTENTE"; // EN_ATTENTE, SAISI

    @OneToOne(mappedBy = "requestItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private LabResult result;

    /** Attach (or replace) the result, keeping both sides in sync. */
    public void setResultValueObject(LabResult res) {
        if (res != null) {
            res.setRequestItem(this);
        }
        this.result = res;
    }
}
