package de.vzg.reposis.wias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "WIAS_PREPRINT_NUMBER")
public class WiasPreprintNumberEntity {

    @Id
    @Column(name = "MCR_ID", length = 64, nullable = false)
    private String mcrId;

    @Column(name = "PREPRINT_NUMBER", nullable = false)
    private int preprintNumber;

    protected WiasPreprintNumberEntity() {
    }

    public WiasPreprintNumberEntity(String mcrId, int preprintNumber) {
        this.mcrId = mcrId;
        this.preprintNumber = preprintNumber;
    }

    public String getMcrId() {
        return mcrId;
    }

    public int getPreprintNumber() {
        return preprintNumber;
    }

    public void setPreprintNumber(int preprintNumber) {
        this.preprintNumber = preprintNumber;
    }
}
