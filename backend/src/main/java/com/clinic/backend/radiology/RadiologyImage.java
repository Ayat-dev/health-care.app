package com.clinic.backend.radiology;

import com.clinic.backend.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An image file attached to a {@link RadiologyRequest}. Files are stored on disk by
 * {@link com.clinic.backend.storage.FileStorageService} and served under {@code /uploads/**};
 * {@code filePath} holds that public web path.
 */
@Entity
@Table(name = "radiology_images")
@Getter @Setter @NoArgsConstructor
public class RadiologyImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "radiology_request_id", nullable = false)
    private RadiologyRequest radiologyRequest;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(length = 255)
    private String caption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
