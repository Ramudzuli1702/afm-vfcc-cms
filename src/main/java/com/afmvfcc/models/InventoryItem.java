package com.afmvfcc.models;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InventoryItem {
    private int         id;
    private String      itemName;
    private String      category;
    private int         quantity;
    private String      unit;
    private String      condition;
    private String      location;
    private String      custodian;
    private LocalDate   purchaseDate;
    private BigDecimal  purchaseValue;
    private int         lowStockThreshold;
    private String      notes;

    public int getId()                              { return id; }
    public void setId(int id)                       { this.id = id; }

    public String getItemName()                     { return itemName; }
    public void setItemName(String itemName)        { this.itemName = itemName; }

    public String getCategory()                     { return category; }
    public void setCategory(String category)        { this.category = category; }

    public int getQuantity()                        { return quantity; }
    public void setQuantity(int quantity)            { this.quantity = quantity; }

    public String getUnit()                          { return unit; }
    public void setUnit(String unit)                 { this.unit = unit; }

    public String getCondition()                    { return condition; }
    public void setCondition(String condition)       { this.condition = condition; }

    public String getLocation()                     { return location; }
    public void setLocation(String location)         { this.location = location; }

    public String getCustodian()                    { return custodian; }
    public void setCustodian(String custodian)        { this.custodian = custodian; }

    public LocalDate getPurchaseDate()              { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public BigDecimal getPurchaseValue()            { return purchaseValue; }
    public void setPurchaseValue(BigDecimal purchaseValue) { this.purchaseValue = purchaseValue; }

    public int getLowStockThreshold()               { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public String getNotes()                        { return notes; }
    public void setNotes(String notes)               { this.notes = notes; }

    public boolean isLowStock() {
        return lowStockThreshold > 0 && quantity <= lowStockThreshold;
    }

    public boolean needsAttention() {
        return "Needs Repair".equals(condition) || "Damaged".equals(condition);
    }
}
