package com.edvenswa.autoaccept;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/**
 * UI translations. Keys are the English strings used in code; anything missing falls back to English.
 * Order of values: Hindi, Telugu. Templates keep their %s placeholders in the same order.
 */
public class I18n {
    static final Map<String, String[]> M = new HashMap<>();

    static final String[] CODES = {"hi", "te", "ta", "kn", "mr", "bn"};

    static String t(SharedPreferences sp, String en) { return tl(sp.getString("lang", "en"), en); }

    /** Translate to an explicit language code ("hi", "ta"...); English when there is no translation. */
    static String tl(String lang, String en) {
        if (lang.equals("en")) return en;
        String[] v = M.get(en);
        if (v == null) return en;
        for (int i = 0; i < CODES.length; i++)
            if (CODES[i].equals(lang)) return v[i] != null ? v[i] : en;
        return en;
    }

    private static String[] row(String en) {
        String[] v = M.get(en);
        if (v == null) { v = new String[CODES.length]; M.put(en, v); }
        return v;
    }

    /** Hindi and Telugu. */
    static void a(String en, String hi, String te) { String[] v = row(en); v[0] = hi; v[1] = te; }

    /** Tamil, Kannada, Marathi, Bengali. */
    static void b(String en, String ta, String kn, String mr, String bn) { String[] v = row(en); v[2] = ta; v[3] = kn; v[4] = mr; v[5] = bn; }

    static {
        // navigation and home
        a("Home", "होम", "హోమ్");
        a("History", "इतिहास", "చరిత్ర");
        a("Money", "पैसा", "డబ్బు");
        a("Settings", "सेटिंग्स", "సెట్టింగ్స్");
        a("Welcome back", "वापसी पर स्वागत है", "తిరిగి స్వాగతం");
        a("Today's earnings", "आज की कमाई", "నేటి సంపాదన");
        a("Service on", "सेवा चालू", "సేవ ఆన్");
        a("Enable service", "सेवा चालू करें", "సేవను ఆన్ చేయండి");
        a("ride", "राइड", "రైడ్");
        a("rides", "राइड", "రైడ్‌లు");
        a("accepted today", "आज स्वीकार की गईं", "నేడు అంగీకరించినవి");
        a("Avg per km", "प्रति किमी औसत", "కిమీకి సగటు");
        a("You keep", "आपकी बचत", "మీకు మిగిలేది");
        a("you keep", "आपकी बचत", "మీకు మిగిలేది");
        a("Worth it", "फायदेमंद", "లాభదాయకం");
        a("Average", "औसत", "సగటు");
        a("Low", "कम", "తక్కువ");
        a("Daily goal", "दैनिक लक्ष्य", "రోజువారీ లక్ష్యం");
        a("Goal reached", "लक्ष्य पूरा हुआ", "లక్ష్యం చేరుకున్నారు");
        a("to go", "बाकी", "మిగిలింది");
        a("of", "में से", "లో");
        a("Rides", "राइड्स", "రైడ్‌లు");
        a("Filters", "फ़िल्टर", "ఫిల్టర్లు");
        a("Earnings", "कमाई", "సంపాదన");
        a("Apps", "ऐप्स", "యాప్‌లు");
        a("Auto-accept", "ऑटो-स्वीकार", "ఆటో-అంగీకారం");
        a("Accept rides that match your filters", "आपके फ़िल्टर से मेल खाने वाली राइड स्वीकार करें", "మీ ఫిల్టర్లకు సరిపోయే రైడ్‌లను అంగీకరించండి");
        a("Earnings chip", "कमाई चिप", "సంపాదన చిప్");
        a("Show fare, fuel cost and a worth-it rating on every ride request", "हर राइड रिक्वेस्ट पर किराया, ईंधन खर्च और फायदे की रेटिंग दिखाएं", "ప్రతి రైడ్ రిక్వెస్ట్‌పై ఛార్జీ, ఇంధన ఖర్చు, లాభ రేటింగ్ చూపించు");
        a("Quick profile", "क्विक प्रोफ़ाइल", "త్వరిత ప్రొఫైల్");
        a("Relaxed", "आराम", "రిలాక్స్డ్");
        a("Peak", "पीक", "పీక్");
        a("Night", "रात", "రాత్రి");
        a("Try a sample ride", "नमूना राइड आज़माएं", "నమూనా రైడ్ ప్రయత్నించండి");
        a("Recent rides", "हाल की राइड", "ఇటీవలి రైడ్‌లు");
        a("View all", "सभी देखें", "అన్నీ చూడండి");
        a("Turn on the service first", "पहले सेवा चालू करें", "ముందు సేవను ఆన్ చేయండి");
        a("Sample shown for 8 seconds. It appears over any app.", "नमूना 8 सेकंड के लिए दिखेगा। यह किसी भी ऐप पर दिखता है।", "నమూనా 8 సెకన్లు కనిపిస్తుంది. ఇది ఏ యాప్ పైనైనా కనిపిస్తుంది.");

        // setup check
        a("Everything is set up", "सब कुछ तैयार है", "అన్నీ సిద్ధంగా ఉన్నాయి");
        a("Service on, battery unrestricted, driver app found.", "सेवा चालू, बैटरी अनरिस्ट्रिक्टेड, ड्राइवर ऐप मिला।", "సేవ ఆన్, బ్యాటరీ అన్‌రిస్ట్రిక్ట్, డ్రైవర్ యాప్ కనిపించింది.");
        a("Setup check", "सेटअप जाँच", "సెటప్ తనిఖీ");
        a("Accessibility service", "एक्सेसिबिलिटी सेवा", "యాక్సెసిబిలిటీ సేవ");
        a("On", "चालू", "ఆన్");
        a("Off. Tap to turn it on.", "बंद है। चालू करने के लिए टैप करें।", "ఆఫ్లో ఉంది. ఆన్ చేయడానికి నొక్కండి.");
        a("Battery use", "बैटरी उपयोग", "బ్యాటరీ వినియోగం");
        a("Unrestricted", "अनरिस्ट्रिक्टेड", "అన్‌రిస్ట్రిక్ట్");
        a("Restricted. Tap to set Unrestricted so Android doesn't stop the service.", "प्रतिबंधित है। अनरिस्ट्रिक्टेड करने के लिए टैप करें ताकि सेवा न रुके।", "పరిమితం. సేవ ఆగకుండా అన్‌రిస్ట్రిక్ట్ చేయడానికి నొక్కండి.");
        a("Driver app", "ड्राइवर ऐप", "డ్రైవర్ యాప్");
        a("Installed", "इंस्टॉल है", "ఇన్‌స్టాల్ అయింది");
        a("No selected driver app found on this phone.", "चुना हुआ ड्राइवर ऐप इस फ़ोन पर नहीं मिला।", "ఎంచుకున్న డ్రైవర్ యాప్ ఈ ఫోన్‌లో లేదు.");

        // profiles / filters summary
        a("Min fare ₹%s", "न्यूनतम किराया ₹%s", "కనీస ఛార్జీ ₹%s");
        a("Pickup up to %s km", "पिकअप %s किमी तक", "పికప్ %s కిమీ వరకు");
        a("At least ₹%s per km", "कम से कम ₹%s प्रति किमी", "కిమీకి కనీసం ₹%s");
        a("No limits set", "कोई सीमा तय नहीं", "పరిమితులు లేవు");

        // ride list and history
        a("No rides accepted yet", "अभी तक कोई राइड स्वीकार नहीं की", "ఇంకా ఏ రైడ్ అంగీకరించలేదు");
        a("Accepted rides appear here with your earnings and a worth-it rating.", "स्वीकार की गई राइड कमाई और फायदे की रेटिंग के साथ यहाँ दिखेंगी।", "అంగీకరించిన రైడ్‌లు సంపాదన, లాభ రేటింగ్తో ఇక్కడ కనిపిస్తాయి.");
        a("fuel", "ईंधन", "ఇంధనం");
        a("Bike", "बाइक", "బైక్");
        a("Bike / Scooty", "बाइक / स्कूटी", "బైక్ / స్కూటీ");
        a("Auto", "ऑटो", "ఆటో");
        a("Car", "कार", "కార్");
        a("Ride history", "राइड इतिहास", "రైడ్ చరిత్ర");
        a("Nothing here yet.", "अभी यहाँ कुछ नहीं है।", "ఇక్కడ ఇంకా ఏదీ లేదు.");
        a("total", "कुल", "మొత్తం");
        a("Last 7 days", "पिछले 7 दिन", "గత 7 రోజులు");
        a("Earned", "कमाया", "సంపాదించింది");
        a("earned", "कमाया", "సంపాదించింది");
        a("Best day", "सबसे अच्छा दिन", "ఉత్తమ రోజు");
        a("Recently skipped", "हाल में छोड़ी गईं", "ఇటీవల దాటవేసినవి");
        a("All rides", "सभी राइड", "అన్ని రైడ్‌లు");
        a("Export as CSV", "CSV के रूप में निर्यात करें", "CSVग\0C3E ఎగుమతి చేయండి");
        a("Clear history", "इतिहास साफ़ करें", "చరిత్ర తుడిపివేయండి");
        a("Clear all accepted-ride history?", "स्वीकार की गई सभी राइड का इतिहास साफ़ करें?", "అంగీకరించిన అన్ని రైడ్ చరిత్రను తుడిపివేయాలా?");
        a("Cancel", "रद्द करें", "రద్దు చేయి");
        a("Clear", "साफ़ करें", "తుడిపివేయి");

        // money
        a("Earnings, expenses and profit.", "कमाई, खर्च और मुनाफा।", "సంపాదన, ఖర్చులు, లాభం.");
        a("Today", "आज", "ఈరోజు");
        a("7 days", "7 दिन", "7 రోజులు");
        a("This month", "इस महीने", "ఈ నెల");
        a("Net profit", "शुद्ध मुनाफा", "నికర లాభం");
        a("Net loss", "शुद्ध नुकसान", "నికర నష్టం");
        a("costs", "लागत", "ఖర్చులు");
        a("Breakdown", "विवरण", "వివరాలు");
        a("Ride earnings", "राइड से कमाई", "రైడ్ సంపాదన");
        a("Other income", "अन्य आय", "ఇతర ఆదాయం");
        a("Fuel (estimated)", "ईंधन (अनुमानित)", "ఇంధనం (అంచనా)");
        a("km driven", "किमी चलाया", "కిమీ నడిపారు");
        a("Other expenses", "अन्य खर्च", "ఇతర ఖర్చులు");
        a("Fixed costs", "निश्चित लागत", "స్థిర ఖర్చులు");
        a("Monthly costs spread over %s days", "मासिक लागत %s दिनों में बाँटी गई", "నెలవారీ ఖర్చులు %s రోజులలో పంచబడ్డాయి");
        a("Per ride and per km", "प्रति राइड और प्रति किमी", "రైడ్‌కు మరియు కిమీకి");
        a("Profit per ride", "प्रति राइड मुनाफा", "రైడ్‌కి లాభం");
        a("Profit per km", "प्रति किमी मुनाफा", "కిమీకి లాభం");
        a("Cost per km", "प्रति किमी लागत", "కిమీకి ఖర్చు");
        a("You are ₹%s above break-even.", "आप ब्रेक-इवन से ₹%s ऊपर हैं।", "మీరు బ్రేక్-ఇవెన్ కన్నా ₹%s ఎక్కువగా ఉన్నారు.");
        a("You need ₹%s more to break even.", "ब्रेक-इवन के लिए आपको ₹%s और चाहिए।", "బ్రేక్-ఇవెన్ అయ్యేందుకు మీకు ఇంకా ₹%s కావాలి.");
        a("By vehicle", "वाहन के अनुसार", "వాహనం ప్రకారం");
        a("By app", "ऐप के अनुसार", "యాప్ ప్రకారం");
        a("Expenses & income", "खर्च और आय", "ఖర్చులు మరియు ఆదాయం");
        a("Add", "जोड़ें", "జోడించు");
        a("Nothing added for this period. Fuel is calculated automatically; add maintenance, tolls, fines or extra income here.", "इस अवधि के लिए कुछ नहीं जोड़ा। ईंधन अपने आप गिना जाता है; मरम्मत, टोल, जुर्माना या अतिरिक्त आय यहाँ जोड़ें।", "ఈ కాలంలో ఏదీ జోడించలేదు. ఇంధనం ఆటోమెటిక్గా లెక్కించబడుతుంది; మరమ్మతు, టోల్, జరిమానాలు లేదా అదనపు ఆదాయం ఇక్కడ జోడించండి.");
        a("Delete this entry?", "यह प्रविष्टि हटाएँ?", "ఈ ఎంట్రీని తొలగించాలా?");
        a("Delete", "हटाएँ", "తొలగించు");
        a("Add entry", "प्रविष्टि जोड़ें", "ఎంట్రీ జోడించు");
        a("Type", "प्रकार", "రకం");
        a("Amount (₹)", "राशि (₹)", "మొత్తం (₹)");
        a("Enter the amount.", "राशि दर्ज करें।", "మొత్తాన్ని నమోదు చేయండి.");
        a("Note (optional)", "नोट (वैकल्पिक)", "నోట్ (ఐచ్ఛికం)");
        a("For example: chain and oil change.", "उदाहरण: चेन और तेल बदलना।", "ఉదాహరణ: చైన్, ఆయిల్ మార్పు.");
        a("Save", "सेव करें", "సేవ్ చేయి");
        a("Income - manual ride", "आय - मैनुअल राइड", "ఆదాయం - మాన్యువల్ రైడ్");
        a("Income - tip or bonus", "आय - टिप या बोनस", "ఆదాయం - టిప్ లేదా బోనస్");
        a("Maintenance", "मरम्मत", "మరమ్మతు");
        a("Parking & toll", "पार्किंग और टोल", "పార్కింగ్ మరియు టోల్");
        a("Fine", "जुर्माना", "జరిమానా");
        a("Phone & data", "फ़ोन और डेटा", "ఫోన్ మరియు డేటా");
        a("Food", "खाना", "ఆహారం");
        a("Other", "अन्य", "ఇతర");

        // settings
        a("Choose which rides are accepted.", "चुनें किन राइड को स्वीकार करना है।", "ఎలాంటి రైడ్‌లను అంగీకరించాలో ఎంచుకోండి.");
        a("Ride filters", "राइड फ़िल्टर", "రైడ్ ఫిల్టర్లు");
        a("Minimum fare (₹)", "न्यूनतम किराया (₹)", "కనీస ఛార్జీ (₹)");
        a("Rides below this fare are skipped. Use 0 for no limit.", "इससे कम किराए वाली राइड छोड़ दी जाती हैं। कोई सीमा नहीं के लिए 0 लिखें।", "ఈ ఛార్జీ కన్నా తక్కువ ఉన్న రైడ్‌లు దాటవేయబడతాయి. పరిమితి లేకుండా 0 వాడండి.");
        a("Maximum pickup distance (km)", "अधिकतम पिकअप दूरी (किमी)", "గరిష్ట పికప్ దూరం (కిమీ)");
        a("Rides with a farther pickup are skipped. Use 0 for no limit.", "आगे के पिकअप वाली राइड छोड़ दी जाती हैं। कोई सीमा नहीं के लिए 0 लिखें।", "మరింత దూరంలో పికప్ ఉన్న రైడ్‌లు దాటవేయబడతాయి. పరిమితి లేకుండా 0 వాడండి.");
        a("Minimum earnings per km (₹)", "न्यूनतम कमाई प्रति किमी (₹)", "కిమీకి కనీస సంపాదన (₹)");
        a("Skips rides that pay less per km. Use 0 for no limit.", "प्रति किमी कम कमाई वाली राइड छोड़ता है। कोई सीमा नहीं के लिए 0 लिखें।", "కిమీకి తక్కువ చెల్లించే రైడ్‌లను దాటవేస్తుంది. పరిమితి లేకుండా 0 వాడండి.");
        a("Response delay (seconds)", "प्रतिक्रिया विलंब (सेकंड)", "స్పందన ఆలస్యం (సెకన్లు)");
        a("Wait this long before accepting.", "स्वीकार करने से पहले इतना इंतज़ार करें।", "అంగీకరించడానికి ముందు ఇంతసేపు ఆగండి.");
        a("Instant-accept radius (km)", "तत्काल स्वीकार दायरा (किमी)", "తక్షణ అంగీకార పరిధి (కిమీ)");
        a("Rides with pickup this close are accepted with no delay. Use 0 to turn off.", "इतने पास के पिकअप वाली राइड बिना देरी स्वीकार होती हैं। बंद करने के लिए 0 लिखें।", "ఇంత దగ్గరలో పికప్ ఉన్న రైడ్‌లు ఆలస్యం లేకుండా అంగీకరించబడతాయి. ఆపివేయడానికి 0 వాడండి.");
        a("Earnings targets", "कमाई लक्ष्य", "సంపాదన లక్ష్యాలు");
        a("Bike / scooty target (₹ per km)", "बाइक / स्कूटी लक्ष्य (₹ प्रति किमी)", "బైక్ / స్కూటీ లక్ష్యం (కిమీకి ₹)");
        a("Rides at or above this are marked Worth it.", "इस स्तर या उससे ऊपर की राइड फायदेमंद मानी जाती हैं।", "ఈ స్థాయిలో లేదా అంతకన్నా ఎక్కువ ఉన్న రైడ్‌లు లాభదాయకంగా మార్క్ అవుతాయి.");
        a("Auto target (₹ per km)", "ऑटो लक्ष्य (₹ प्रति किमी)", "ఆటో లక్ష్యం (కిమీకి ₹)");
        a("Car target (₹ per km)", "कार लक्ष्य (₹ प्रति किमी)", "కార్ లక్ష్యం (కిమీకి ₹)");
        a("Vehicle & fuel", "वाहन और ईंधन", "వాహనం మరియు ఇంధనం");
        a("My vehicle", "मेरा वाहन", "నా వాహనం");
        a("Used when the ride type isn't shown on the request.", "जब रिक्वेस्ट पर राइड का प्रकार नहीं दिखता तब इसका उपयोग होता है।", "రిక్వెస్ట్‌లో రైడ్ రకం కనిపించనప్పుడు వీటిని వాడతారు.");
        a("Fuel price (₹ per litre)", "ईंधन की कीमत (₹ प्रति लीटर)", "ఇంధన ధర (లీటరుకు ₹)");
        a("Current fuel price in your city.", "आपके शहर में वर्तमान ईंधन की कीमत।", "మీ నగరంలో ప్రస్తుత ఇంధన ధర.");
        a("Bike / scooty mileage (km per litre)", "बाइक / स्कूटी माइलेज (किमी प्रति लीटर)", "బైక్ / స్కూటీ మైలేజ్ (లీటరుకు కిమీ)");
        a("Used to work out fuel cost per km.", "प्रति किमी ईंधन लागत निकालने में उपयोग।", "కిమీకి ఇంధన ఖర్చు లెక్కించడానికి వాడతారు.");
        a("Auto mileage (km per litre)", "ऑटो माइलेज (किमी प्रति लीटर)", "ఆటో మైలేజ్ (లీటరుకు కిమీ)");
        a("Car mileage (km per litre)", "कार माइलेज (किमी प्रति लीटर)", "కార్ మైలేజ్ (లీటరుకు కిమీ)");
        a("Monthly fixed costs (₹)", "मासिक निश्चित लागत (₹)", "నెలవారీ స్థిర ఖర్చులు (₹)");
        a("EMI, insurance, phone plan. Spread evenly across the days in Money.", "EMI, बीमा, फ़ोन प्लान। मनी में दिनों में बराबर बाँटा जाता है।", "EMI, బీమా, ఫోన్ ప్లాన్. డబ్బు ట్యాబ్‌లో రోజులకు సమానంగా పంచబడుతుంది.");
        a("Goals & alerts", "लक्ष्य और अलर्ट", "లక్ష్యాలు మరియు అలర్ట్‌లు");
        a("Daily earnings goal (₹)", "दैनिक कमाई लक्ष्य (₹)", "రోజువారీ సంపాదన లక్ష్యం (₹)");
        a("Shows progress on the home screen. Use 0 to hide.", "होम स्क्रीन पर प्रगति दिखाता है। छिपाने के लिए 0 लिखें।", "హోమ్ స్క్రీన్‌పై పురోగతిని చూపుతుంది. దాచడానికి 0 వాడండి.");
        a("Vibrate and beep for Worth it rides", "फायदेमंद राइड पर कंपन और बीप", "లాభదాయక రైడ్‌లపై విబ్రేషన్ మరియు బీప్");
        a("Decline rides rated Low", "कम रेटिंग वाली राइड अस्वीकार करें", "తక్కువ రేటింగ్ రైడ్‌లను తిరస్కరించండి");
        a("Be stricter in peak hours", "पीक आवर्स में अधिक सख्त रहें", "పీక్ ఆవర్లలో మరింత కఠినంగా ఉండండి");
        a("Taps Reject on Low rides. Off by default; a wrong match would lose you a ride.", "कम राइड पर रिजेक्ट दबाता है। डिफ़ॉल्ट रूप से बंद; गलत मेल से राइड छूट सकती है।", "తక్కువ రైడ్‌లపై రిజెక్ట్ నొక్కుతుంది. డిఫాల్ట్గా ఆఫ్; తప్పు సరిపోలికతో రైడ్ పోవచ్చు.");
        a("Peak hours start (0-23)", "पीक आवर्स शुरू (0-23)", "పీక్ ఆవర్లు ప్రారంభం (0-23)");
        a("Hour of day, 24-hour clock.", "दिन का घंटा, 24 घंटे की घड़ी।", "రోజులో గంట, 24 గంటల గడియారం.");
        a("Peak hours end (0-23)", "पीक आवर्स समाप्त (0-23)", "పీక్ ఆవర్లు ముగింపు (0-23)");
        a("Extra target in peak hours (₹ per km)", "पीक आवर्स में अतिरिक्त लक्ष्य (₹ प्रति किमी)", "పీక్ ఆవర్లలో అదనపు లక్ష్యం (కిమీకి ₹)");
        a("Added to your targets during peak hours.", "पीक आवर्स में आपके लक्ष्य में जोड़ा जाता है।", "పీక్ ఆవర్లలో మీ లక్ష్యాలకు కలుపబడుతుంది.");
        a("Display & language", "डिस्प्ले और भाषा", "డిస్ప్లే మరియు భాష");
        a("Language", "भाषा", "భాష");
        a("Earnings chip position", "कमाई चिप की जगह", "సంపాదన చిప్ స్థానం");
        a("Top", "ऊपर", "పైన");
        a("Bottom", "नीचे", "క్రింద");
        a("Show the floating Vamsify bubble over driver apps", "ड्राइवर ऐप पर तैरता हुआ Vamsify बबल दिखाएं", "డ్రైవర్ యాప్‌ల పై తేలుతున్న Vamsify బబుల్ చూపించండి");
        a("Large text and bigger buttons", "बड़ा टेक्स्ट और बड़े बटन", "పెద్ద అక్షరాలు మరియు పెద్ద బటన్‌లు");
        a("One-hand layout (main controls at the bottom)", "एक हाथ वाला लेआउट (मुख्य नियंत्रण नीचे)", "ఒక చేతి లేఅవుట్ (ముఖ్య నియంత్రణలు క్రింద)");
        a("Pause and resume notification", "पॉज़ और रिज्यूम नोटिफ़िकेशन", "పాజ్ మరియు రెజ్యూమ్ నోటిఫికేషన్");
        a("Daily summary at 9 PM", "रात 9 बजे दैनिक सारांश", "రాత్రి 9కి రోజువారీ సారాంశం");
        a("Driver apps", "ड्राइवर ऐप्स", "డ్రైవర్ యాప్‌లు");
        a("Save changes", "बदलाव सेव करें", "మార్పులు సేవ్ చేయి");
        a("Changes saved", "बदलाव सेव हो गए", "మార్పులు సేవ్ అయ్యాయి");

        // first-run setup
        a("Step %s of 3", "चरण %s / 3", "దశ %s / 3");
        a("Welcome to Vamsify", "Vamsify में आपका स्वागत है", "Vamsifyకి స్వాగతం");
        a("Tell us about your vehicle so we can estimate fuel cost and profit on every ride.", "अपने वाहन के बारे में बताएं ताकि हर राइड पर ईंधन खर्च और मुनाफा आंका जा सके।", "మీ వాహనం గురించి చెప్పండి, అప్పుడు ప్రతి రైడ్‌లో ఇంధన ఖర్చు, లాభం అంచనా వేయగలము.");
        a("Next", "आगे", "తదుపరి");
        a("Turn on the service", "सेवा चालू करें", "సేవను ఆన్ చేయండి");
        a("Vamsify needs Accessibility access to read ride requests and show the earnings chip.", "Vamsify को राइड रिक्वेस्ट पढ़ने और कमाई चिप दिखाने के लिए एक्सेसिबिलिटी एक्सेस चाहिए।", "రైడ్ రిక్వెస్ట్‌లను చదవడానికి, సంపాదన చిప్ చూపడానికి Vamsifyకి యాక్సెసిబిలిటీ యాక్సెస్ కావాలి.");
        a("1. Tap the button below and open Installed apps.", "1. नीचे का बटन दबाएँ और इंस्टॉल्ड ऐप्स खोलें।", "1. క్రింది బటన్ నొక్కి ఇన్‌స్టాల్ చేసిన యాప్‌లు తెరవండి.");
        a("2. Choose Vamsify and switch it on.", "2. Vamsify चुनें और इसे चालू करें।", "2. Vamsify ఎంచుకోండి మరియు ఆన్ చేయండి.");
        a("3. If it is greyed out: open App info, tap the menu at the top right and choose Allow restricted settings, then try again.", "3. अगर यह ग्रे है: ऐप इंफ़ो खोलें, ऊपर दाएँ मेनू पर टैप करें और Allow restricted settings चुनें, फिर से कोशिश करें।", "3. గ్రే అయితే: యాప్ ఇన్ఫో తెరిచి, పై కుడి మెనూ నొక్కి Allow restricted settings ఎంచుకుని మళ్లీ ప్రయత్నించండి.");
        a("Open Accessibility settings", "एक्सेसिबिलिटी सेटिंग्स खोलें", "యాక్సెసిబిలిటీ సెట్టింగ్స్ తెరవండి");
        a("Service is running.", "सेवा चल रही है।", "సేవ నడుస్తోంది.");
        a("Service is not on yet. You can also do this later from Home.", "सेवा अभी चालू नहीं है। आप यह बाद में होम से भी कर सकते हैं।", "సేవ ఇంకా ఆన్ లేదు. దీన్ని తరువాత హోమ్ నుండి కూడా చేయవచ్చు.");
        a("Choose your driver apps", "अपने ड्राइवर ऐप्स चुनें", "మీ డ్రైవర్ యాప్‌లు ఎంచుకోండి");
        a("Vamsify only works inside the apps you select.", "Vamsify केवल आपके चुने हुए ऐप्स में काम करता है।", "Vamsify మీరు ఎంచుకున్న యాప్‌లలోమాత్రమే పనిచేస్తుంది.");
        a("Start", "शुरू करें", "ప్రారంభించండి");

        // service: chip, notifications, reasons
        a("Auto-accept is on", "ऑटो-स्वीकार चालू है", "ఆటో-అంగీకారం ఆన్‌లో ఉంది");
        a("Auto-accept is paused", "ऑटो-स्वीकार रुका है", "ఆటో-అంగీకారం ఆగింది");
        a("Pause", "रोकें", "ఆపండి");
        a("Resume", "फिर शुरू करें", "తిరిగి ప్రారంభించండి");
        a("Today's summary", "आज का सारांश", "నేటి సారాంశం");
        a("profit", "मुनाफा", "లాభం");
        a("loss", "नुकसान", "నష్టం");
        a("best hour", "सबसे अच्छा घंटा", "ఉత్తమ గంట");
        a("Fare", "किराया", "ఛార్జీ");
        a("Distance not shown on this request", "इस रिक्वेस्ट पर दूरी नहीं दिखाई गई", "ఈ రిక్వెస్ట్‌లో దూరం చూపలేదు");
        a("Not auto-accepted", "ऑटो-स्वीकार नहीं हुआ", "ఆటో-అంగీకరించలేదు");
        a("Fare ₹%s is below your minimum of ₹%s", "किराया ₹%s आपके न्यूनतम ₹%s से कम है", "ఛార్జీ ₹%s మీ కనీసం ₹%s కన్నా తక్కువ");
        a("Pickup distance not shown, so it can't be checked", "पिकअप दूरी नहीं दिखी, इसलिए जाँच नहीं हो सकती", "పికప్ దూరం చూపలేదు, అందువల్ల తనిఖీ చేయలేము");
        a("Pickup %s km is farther than your limit of %s km", "पिकअप %s किमी आपकी सीमा %s किमी से अधिक दूर है", "పికప్ %s కిమీ మీ పరిమితి %s కిమీ కన్నా ఎక్కువ దూరం");
        a("Distance not shown, so earnings per km can't be checked", "दूरी नहीं दिखी, इसलिए प्रति किमी कमाई की जाँच नहीं हो सकती", "దూరం చూపలేదు, అందువల్ల కిమీకి సంపాదన తనిఖీ చేయలేము");
        a("Pays ₹%s per km, below your minimum of ₹%s", "₹%s प्रति किमी देती है, आपके न्यूनतम ₹%s से कम", "కిమీకి ₹%s ఇస్తుంది, మీ కనీసం ₹%s కన్నా తక్కువ");
    }

    static { MoreLanguages.register(); NewStrings.register(); }
}
