package com.unilodge.persistence.entity;

import com.unilodge.common.enums.FurnishedStatus;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.PropertyType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A rental listing published by a landlord. */
@Entity
@Table(name = "properties")
public class Property extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 30)
    private PropertyType propertyType;

    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(nullable = false)
    private boolean negotiable;

    @Column(name = "utilities_included", nullable = false)
    private boolean utilitiesIncluded;

    @Column(name = "utilities_description", length = 300)
    private String utilitiesDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FurnishedStatus furnished;

    @Column(nullable = false)
    private Integer bedrooms;

    @Column(nullable = false)
    private Integer bathrooms;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "distance_to_campus_km")
    private Double distanceToCampusKm;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "lease_terms", length = 500)
    private String leaseTerms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListingStatus status;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "property_amenities", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "amenity", length = 60)
    private Set<String> amenities = new LinkedHashSet<>();

    @OneToMany(mappedBy = "property", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PropertyImage> images = new ArrayList<>();

    public User getLandlord() {
        return landlord;
    }

    public void setLandlord(User landlord) {
        this.landlord = landlord;
    }

    public University getUniversity() {
        return university;
    }

    public void setUniversity(University university) {
        this.university = university;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public BigDecimal getRentAmount() {
        return rentAmount;
    }

    public void setRentAmount(BigDecimal rentAmount) {
        this.rentAmount = rentAmount;
    }

    public boolean isNegotiable() {
        return negotiable;
    }

    public void setNegotiable(boolean negotiable) {
        this.negotiable = negotiable;
    }

    public boolean isUtilitiesIncluded() {
        return utilitiesIncluded;
    }

    public void setUtilitiesIncluded(boolean utilitiesIncluded) {
        this.utilitiesIncluded = utilitiesIncluded;
    }

    public String getUtilitiesDescription() {
        return utilitiesDescription;
    }

    public void setUtilitiesDescription(String utilitiesDescription) {
        this.utilitiesDescription = utilitiesDescription;
    }

    public FurnishedStatus getFurnished() {
        return furnished;
    }

    public void setFurnished(FurnishedStatus furnished) {
        this.furnished = furnished;
    }

    public Integer getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(Integer bedrooms) {
        this.bedrooms = bedrooms;
    }

    public Integer getBathrooms() {
        return bathrooms;
    }

    public void setBathrooms(Integer bathrooms) {
        this.bathrooms = bathrooms;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Double getDistanceToCampusKm() {
        return distanceToCampusKm;
    }

    public void setDistanceToCampusKm(Double distanceToCampusKm) {
        this.distanceToCampusKm = distanceToCampusKm;
    }

    public LocalDate getMoveInDate() {
        return moveInDate;
    }

    public void setMoveInDate(LocalDate moveInDate) {
        this.moveInDate = moveInDate;
    }

    public String getLeaseTerms() {
        return leaseTerms;
    }

    public void setLeaseTerms(String leaseTerms) {
        this.leaseTerms = leaseTerms;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public Set<String> getAmenities() {
        return amenities;
    }

    public void setAmenities(Set<String> amenities) {
        this.amenities = amenities;
    }

    public List<PropertyImage> getImages() {
        return images;
    }
}
