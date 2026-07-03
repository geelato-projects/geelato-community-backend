package cn.geelato.web.platform.resolve.biz.carrierso;

import java.util.List;

public final class CarrierSoFieldKeys {
    public static final String CARRIER_CODE = "carrierCode";
    public static final String CARRIER_NAME = "carrierName";
    public static final String BOOKING_NO = "bookingNo";
    public static final String SO_NO = "soNo";
    public static final String SHIPPER_NAME = "shipperName";
    public static final String CONSIGNEE_NAME = "consigneeName";
    public static final String NOTIFY_PARTY = "notifyParty";
    public static final String VESSEL_NAME = "vesselName";
    public static final String VOYAGE_NO = "voyageNo";
    public static final String POL_CODE = "polCode";
    public static final String POL_NAME = "polName";
    public static final String POD_CODE = "podCode";
    public static final String POD_NAME = "podName";
    public static final String FINAL_DESTINATION = "finalDestination";
    public static final String PLACE_OF_RECEIPT = "placeOfReceipt";
    public static final String PLACE_OF_DELIVERY = "placeOfDelivery";
    public static final String ETD = "etd";
    public static final String ETA = "eta";
    public static final String CARGO_DESCRIPTION = "cargoDescription";
    public static final String PACKAGE_QTY = "packageQty";
    public static final String PACKAGE_UNIT = "packageUnit";
    public static final String GROSS_WEIGHT = "grossWeight";
    public static final String WEIGHT_UNIT = "weightUnit";
    public static final String MEASUREMENT = "measurement";
    public static final String MEASUREMENT_UNIT = "measurementUnit";
    public static final String PAYMENT_TERM = "paymentTerm";
    public static final String SERVICE_TYPE = "serviceType";
    public static final String CONTRACT_NO = "contractNo";
    public static final String CUSTOMER_REF_NO = "customerRefNo";
    public static final String CONTAINERS = "containers";
    public static final String REMARKS = "remarks";

    public static final List<String> STANDARD_KEYS = List.of(
            CARRIER_CODE,
            CARRIER_NAME,
            BOOKING_NO,
            SO_NO,
            SHIPPER_NAME,
            CONSIGNEE_NAME,
            NOTIFY_PARTY,
            VESSEL_NAME,
            VOYAGE_NO,
            POL_CODE,
            POL_NAME,
            POD_CODE,
            POD_NAME,
            FINAL_DESTINATION,
            PLACE_OF_RECEIPT,
            PLACE_OF_DELIVERY,
            ETD,
            ETA,
            CARGO_DESCRIPTION,
            PACKAGE_QTY,
            PACKAGE_UNIT,
            GROSS_WEIGHT,
            WEIGHT_UNIT,
            MEASUREMENT,
            MEASUREMENT_UNIT,
            PAYMENT_TERM,
            SERVICE_TYPE,
            CONTRACT_NO,
            CUSTOMER_REF_NO,
            CONTAINERS,
            REMARKS
    );

    public static final List<String> MAPPING_KEYS = List.of(
            CARRIER_CODE,
            SHIPPER_NAME,
            CONSIGNEE_NAME,
            NOTIFY_PARTY,
            VESSEL_NAME,
            VOYAGE_NO,
            POL_NAME,
            POD_NAME,
            FINAL_DESTINATION,
            PAYMENT_TERM,
            SERVICE_TYPE
    );

    private CarrierSoFieldKeys() {
    }
}
