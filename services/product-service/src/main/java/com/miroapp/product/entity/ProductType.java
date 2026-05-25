package com.miroapp.product.entity;

// =====================================================================
// ProductType — enum de tipos de producto en MIRO
// =====================================================================
// Debe coincidir EXACTAMENTE con el CHECK constraint de la BD:
//   CHECK (type IN ('PHYSICAL','SERVICE','DIGITAL'))
public enum ProductType {
    PHYSICAL,
    SERVICE,
    DIGITAL
}