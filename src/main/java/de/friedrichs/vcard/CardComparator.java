package de.friedrichs.vcard;

import ezvcard.VCard;

import java.util.Comparator;

public class CardComparator implements Comparator<VCard> {
    @Override
    public int compare(VCard o1, VCard o2) {

        return o1.getStructuredName().compareTo(o2.getStructuredName());
//
//        if (o1.getStructuredName() != null && o2.getStructuredName() != null){
//            StructuredName structuredName1 = o1.getStructuredName();
//            StructuredName structuredName2 = o2.getStructuredName();
//            if(structuredName1.getFamily() != null && !structuredName1.getFamily().isEmpty() &&
//                    structuredName2.getFamily() != null && !structuredName2.getFamily().isEmpty()){
//
//                if (structuredName1.getFamily().compareTo(structuredName2.getFamily()) != 0){
//                    return structuredName1.getFamily().compareTo(structuredName2.getFamily());
//                }
//                if(structuredName1.getGiven() != null && !structuredName1.getGiven().isEmpty() &&
//                        structuredName2.getGiven() != null && !structuredName2.getGiven().isEmpty()){
//                    return structuredName1.getGiven().compareTo(structuredName2.getGiven());
//                }
//            }
//        }
//        return 0;
    }
}
