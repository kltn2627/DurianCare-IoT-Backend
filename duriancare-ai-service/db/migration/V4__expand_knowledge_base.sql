-- V4__expand_knowledge_base.sql
-- Expand the durian agricultural knowledge base with richer disease knowledge,
-- stronger biological guidance, organic practices, monitoring advice, and export discipline.

UPDATE kb_diseases
SET
    disease_summary = 'Algal leaf spot on durian is a moisture-driven foliar problem caused by Cephaleuros virescens. It typically starts as orange-red to rust-colored leaf lesions and becomes more severe when the canopy stays wet, shaded, and poorly ventilated.',
    favorable_conditions = 'Prolonged leaf wetness, high humidity, rainy periods, dense canopy, low light penetration, and stressed trees.'
WHERE code = 'ALGAL_LEAF_SPOT';

UPDATE kb_diseases
SET
    disease_summary = 'Leaf blight on durian is a destructive foliar disease associated with Rhizoctonia solani AG1-ID. It can spread rapidly during warm, wet weather and is favored by dense canopies, splash dispersal, and prolonged leaf wetness.',
    favorable_conditions = 'Warm humid weather, repeated rain splash, dense canopy, poor drainage, and extended foliage wetness.'
WHERE code = 'LEAF_BLIGHT';

UPDATE kb_diseases
SET
    disease_summary = 'Phomopsis leaf spot on durian is a latent foliar disease caused by Phomopsis durionis. Small dark lesions may remain subtle for a period, then expand under warm humid conditions and repeated rain splash.',
    favorable_conditions = 'Warm humid weather, rainy periods, latent infection in tissues, crowded canopy, and rain splash spread.'
WHERE code = 'PHOMOPSIS_LEAF_SPOT';

UPDATE kb_diseases
SET
    disease_summary = 'Durian psyllid attack is a sap-sucking pest problem caused by Allocaridara malayensis. The insect prefers tender flushes, causing curling, yellowing, deformity, and reduced vigor of young leaves.',
    favorable_conditions = 'Repeated flushes, low natural enemy pressure, dense young foliage, and weak orchard scouting.'
WHERE code = 'ALLOCARIDARA_ATTACK';

UPDATE kb_diseases
SET
    disease_summary = 'Healthy durian foliage is the target state for the orchard. The leaf shows balanced color, no spotted lesions, no curling, no scorch, and no evidence of pest feeding or latent infection.',
    favorable_conditions = 'Balanced nutrition, good canopy ventilation, clean orchard hygiene, and regular IPM scouting.'
WHERE code = 'HEALTHY_LEAF';

INSERT INTO kb_reference_sources (
    source_code,
    source_name,
    source_type,
    publication_title,
    publisher,
    publication_year,
    url,
    confidence_level,
    notes
) VALUES
    (
        'FAO_SUPERFRUIT_EXPORT',
        'Food and Agriculture Organization of the United Nations',
        'STANDARD',
        'Superfruit proceedings and postharvest handling guidance',
        'FAO',
        2010,
        'https://www.fao.org/fileadmin/templates/est/COMM_MARKETS_MONITORING/Fruits_Vegetables/Documents/superfruit_proceedings.pdf',
        0.930,
        'Supports pruning, canopy management, maturity discipline, and residue minimization for export readiness.'
    ),
    (
        'IR4_ALGAL_LEAF_SPOT',
        'IR-4 Environmental Horticulture Program',
        'DATABASE',
        'Algal Leaf Spot Efficacy Summary 2019',
        'NC State IR-4',
        2019,
        'https://ir4.cals.ncsu.edu/ehc/RegSupport/ResearchSummary/AlgalLeafSpotEfficacySummary2019.pdf',
        0.920,
        'Copper-based efficacy summary for algal leaf spot management.'
    ),
    (
        'PMC_RHIZOCTONIA_BIOCNTROL',
        'PubMed Central',
        'PEER_REVIEWED',
        'Comparative Study of Three Biological Control Agents and Two Fungicides Against Rhizoctonia solani',
        'PMC',
        2023,
        'https://pmc.ncbi.nlm.nih.gov/articles/PMC10141358/',
        0.950,
        'Supports Trichoderma and Bacillus as biocontrol directions against Rhizoctonia solani.'
    ),
    (
        'CABI_RHIZOCTONIA_FUNGICIDES',
        'CABI Digital Library',
        'DATABASE',
        'Efficacy of Fungicides Against Rhizoctonia solani',
        'CABI',
        2023,
        'https://www.cabidigitallibrary.org/doi/pdf/10.5555/20230431711',
        0.940,
        'Supports DMI and protectant fungicide options that require label verification.'
    ),
    (
        'UC_IPM_PHOMOPSIS',
        'University of California Agriculture and Natural Resources',
        'EXTENSION',
        'Phomopsis cane and leafspot',
        'UC IPM',
        2026,
        'https://ipm.ucanr.edu/agriculture/grape/phomopsis-cane-and-leafspot/',
        0.950,
        'Supports sanitation, pre-disease spray discipline, and fungicide rotation principles.'
    ),
    (
        'CORNELL_BEAUVERIA',
        'Cornell University Integrated Pest Management',
        'EXTENSION',
        'Beauveria bassiana fact sheet',
        'Cornell University',
        2025,
        'https://cals.cornell.edu/integrated-pest-management/outreach-education/fact-sheets/beauveria-bassiana',
        0.950,
        'Supports entomopathogenic fungus guidance for sap-sucking insect control.'
    ),
    (
        'FAO_ENTOMOPATHOGENIC_FUNGI',
        'Food and Agriculture Organization of the United Nations',
        'OFFICIAL',
        'Biological control using natural enemies and entomopathogenic fungi',
        'FAO Forestry Department',
        2000,
        'https://www.fao.org/4/al008e/al008e00.pdf',
        0.900,
        'Supports Metarhizium anisopliae and Beauveria bassiana as sustainable pest management tools.'
    )
ON CONFLICT (source_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- ALGAL_LEAF_SPOT
-- ---------------------------------------------------------------------------

INSERT INTO kb_disease_symptoms (
    disease_code,
    symptom_order,
    symptom_text,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 4, 'Mảng bệnh chuyển từ vàng cam sang nâu đỏ và có thể lan rộng trên phiến lá.', 'HGIC_ALGAL_LEAF_SPOT', 0.900),
    ('ALGAL_LEAF_SPOT', 5, 'Bề mặt vết bệnh trở nên sần, như có lớp nhung hoặc lớp crust màu gỉ sắt.', 'IR4_ALGAL_LEAF_SPOT', 0.880)
ON CONFLICT (disease_code, symptom_order) DO NOTHING;

INSERT INTO kb_disease_causes (
    disease_code,
    cause_order,
    cause_text,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 2, 'Bệnh thường phát triển mạnh khi lá bị ướt kéo dài và tán cây bị che bóng.', 'HGIC_ALGAL_LEAF_SPOT', 0.900)
ON CONFLICT (disease_code, cause_order) DO NOTHING;

INSERT INTO kb_biological_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    mechanism,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 1, 'Loại bỏ ngay lá, cành nhỏ và mô bị bệnh để giảm nguồn lây trên tán.', 'Sanitation cắt đứt nguồn inoculum lây lan trong tán và trên mặt đất.', 'HGIC_ALGAL_LEAF_SPOT', 0.940),
    ('ALGAL_LEAF_SPOT', 2, 'Thu gom và tiêu hủy lá rụng có vết bệnh thay vì để mục dưới tán.', 'Giảm nguồn bào tử/tồn dư bệnh trong vườn.', 'HGIC_ALGAL_LEAF_SPOT', 0.920),
    ('ALGAL_LEAF_SPOT', 3, 'Tỉa bớt cành dày để cải thiện ánh sáng và lưu thông khí trong tán.', 'Làm khô lá nhanh hơn sau mưa, giảm điều kiện thuận lợi cho tảo ký sinh.', 'FAO_IPM', 0.930),
    ('ALGAL_LEAF_SPOT', 4, 'Hạn chế tưới phun lên tán; ưu tiên tưới gốc để giảm thời gian lá ướt.', 'Giảm leaf wetness là biện pháp sinh học/canh tác cốt lõi với bệnh phụ thuộc ẩm.', 'FAO_IPM', 0.930),
    ('ALGAL_LEAF_SPOT', 5, 'Duy trì cân bằng dinh dưỡng, tránh thiếu hoặc thừa đạm làm cây suy yếu.', 'Cây khỏe có khả năng chống chịu tốt hơn với bệnh lá.', 'FAO_IPM', 0.920),
    ('ALGAL_LEAF_SPOT', 6, 'Theo dõi chặt sau mưa và sau các đợt ẩm kéo dài để xử lý sớm.', 'Phát hiện sớm giúp hạn chế lan rộng trước khi can thiệp mạnh.', 'HGIC_ALGAL_LEAF_SPOT', 0.900)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_organic_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 2, 'Phun neem extract hoặc botanical spray ở giai đoạn bệnh nhẹ để hỗ trợ chương trình IPM.', 'Ưu tiên liều nhẹ, phun ngoài giờ nắng gắt và không thay thế vệ sinh vườn.', 'FAO_IPM', 0.800),
    ('ALGAL_LEAF_SPOT', 3, 'Sử dụng garlic extract hoặc hỗn hợp thảo mộc được phép trong mô hình canh tác hữu cơ.', 'Chỉ dùng như biện pháp hỗ trợ và kiểm tra độ an toàn cho lá non.', 'FAO_IPM', 0.780),
    ('ALGAL_LEAF_SPOT', 4, 'Bổ sung compost tea hoặc chế phẩm vi sinh để cải thiện sức khỏe tán lá và đất.', 'Dùng chế phẩm đã ủ đúng quy trình, tránh làm lá ướt quá lâu sau phun.', 'FAO_IPM', 0.770),
    ('ALGAL_LEAF_SPOT', 5, 'Thực hiện kiểm tra lá dưới tán và theo dõi lại 3-5 ngày sau mưa lớn.', 'Ghi chép vết bệnh mới để đánh giá tốc độ lây lan.', 'HGIC_ALGAL_LEAF_SPOT', 0.850)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_chemical_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 2, 'Chỉ dùng thuốc gốc đồng như copper hydroxide hoặc copper oxychloride khi bệnh nặng và nhãn tại Việt Nam cho phép.', 'Phải kiểm tra nhãn, PHI, và không lạm dụng phun lặp trên lá còn non.', 'IR4_ALGAL_LEAF_SPOT', 0.840)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_active_ingredients (
    ingredient_name,
    chemical_group,
    source_code,
    confidence_level
) VALUES
    ('Copper hydroxide', 'Copper compound', 'IR4_ALGAL_LEAF_SPOT', 0.850)
ON CONFLICT (ingredient_name) DO NOTHING;

INSERT INTO kb_recommended_chemicals (
    chemical_treatment_id,
    recommendation_order,
    product_name,
    active_ingredient_summary,
    usage_note,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'ALGAL_LEAF_SPOT' AND treatment_order = 2),
        2,
        'Copper hydroxide fungicide',
        'Copper compound used only after sanitation and canopy correction.',
        'Verify exact durian label, dose, and preharvest interval before use.',
        'IR4_ALGAL_LEAF_SPOT',
        0.840
    )
ON CONFLICT (chemical_treatment_id, recommendation_order) DO NOTHING;

INSERT INTO kb_recommended_chemical_active_ingredients (
    recommended_chemical_id,
    active_ingredient_id,
    sort_order
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Copper hydroxide fungicide' ORDER BY created_at DESC LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Copper hydroxide'),
        1
    )
ON CONFLICT (recommended_chemical_id, active_ingredient_id) DO NOTHING;

INSERT INTO kb_harvest_intervals (
    recommended_chemical_id,
    market_code,
    phi_days,
    notes,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Copper hydroxide fungicide' ORDER BY created_at DESC LIMIT 1),
        'VN',
        NULL,
        'Durian-specific PHI must be verified on the approved label before use.',
        'VIETNAM_PPD_LAW',
        0.800
    )
ON CONFLICT (recommended_chemical_id, market_code) DO NOTHING;

INSERT INTO kb_export_requirements (
    disease_code,
    market_code,
    requirement_order,
    requirement_text,
    warning_text,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 'VN', 2, 'Keep spray records and label copies for every orchard block treated.', 'Do not assume a copper spray is harmless for export lots without recordkeeping.', 'VIETNAM_PPD_LAW', 0.900),
    ('ALGAL_LEAF_SPOT', 'EU', 2, 'Observe PHI and verify the residue rule for the exact active ingredient before packing.', 'Residue compliance should be proven with traceability records.', 'EU_PESTICIDES_DB', 0.930)
ON CONFLICT (disease_code, market_code, requirement_order) DO NOTHING;

-- ---------------------------------------------------------------------------
-- LEAF_BLIGHT
-- ---------------------------------------------------------------------------

INSERT INTO kb_disease_symptoms (
    disease_code,
    symptom_order,
    symptom_text,
    source_code,
    confidence_level
) VALUES
    ('LEAF_BLIGHT', 4, 'Vết bệnh thường khởi đầu như đốm nước hoặc đốm nâu nhạt rồi lan nhanh thành mảng cháy lớn.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.970),
    ('LEAF_BLIGHT', 5, 'Trong điều kiện ẩm cao có thể thấy sợi nấm trắng mảnh hoặc vùng mô bị ướt sũng.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.930),
    ('LEAF_BLIGHT', 6, 'Lá nặng bệnh chuyển nâu sẫm, mất độ cứng và rũ xuống trước khi khô.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.920)
ON CONFLICT (disease_code, symptom_order) DO NOTHING;

INSERT INTO kb_disease_causes (
    disease_code,
    cause_order,
    cause_text,
    source_code,
    confidence_level
) VALUES
    ('LEAF_BLIGHT', 2, 'Tồn dư tàn dư bệnh trong vườn và lá ướt kéo dài tạo điều kiện cho nấm phát triển mạnh.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.960)
ON CONFLICT (disease_code, cause_order) DO NOTHING;

INSERT INTO kb_biological_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    mechanism,
    source_code,
    confidence_level
) VALUES
    ('LEAF_BLIGHT', 2, 'Ưu tiên Trichoderma spp. trong các chương trình sinh học để cạnh tranh và ức chế Rhizoctonia solani.', 'Trichoderma là tác nhân đối kháng phổ biến giúp giảm áp lực nấm bệnh.', 'PMC_RHIZOCTONIA_BIOCNTROL', 0.920),
    ('LEAF_BLIGHT', 3, 'Bổ sung Bacillus spp. hoặc các vi khuẩn có lợi đã được kiểm chứng trong chương trình IPM.', 'Bacillus spp. hỗ trợ ức chế nấm và tăng sức đề kháng của cây.', 'PMC_RHIZOCTONIA_BIOCNTROL', 0.900),
    ('LEAF_BLIGHT', 4, 'Loại bỏ lá bệnh, cành bệnh và vật liệu thực vật nhiễm bệnh khỏi vườn ngay khi phát hiện.', 'Giảm inoculum, cắt đứt chu kỳ bệnh trong điều kiện mưa ẩm.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.960),
    ('LEAF_BLIGHT', 5, 'Tỉa cành để mở tán, tăng nắng và làm khô lá nhanh hơn sau mưa.', 'Tán thoáng làm giảm thời gian lá ướt, vốn rất quan trọng với leaf blight.', 'FAO_IPM', 0.930),
    ('LEAF_BLIGHT', 6, 'Giảm ẩm bề mặt lá bằng cách hạn chế tưới phun, đặc biệt vào chiều tối.', 'Giảm splash dispersal và hạn chế phát sinh bệnh trong đêm ẩm.', 'FAO_IPM', 0.920),
    ('LEAF_BLIGHT', 7, 'Theo dõi sau các đợt mưa lớn và xử lý theo IPM trước khi bệnh lan rộng.', 'Scouting sớm giúp chọn biện pháp nhẹ trước khi cần hóa chất.', 'ISHS_LEAF_ASSAY', 0.910)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_organic_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('LEAF_BLIGHT', 2, 'Phun neem extract hoặc botanical spray như một lớp hỗ trợ trong giai đoạn áp lực bệnh thấp.', 'Chỉ dùng hỗ trợ; không thay thế tỉa tán và vệ sinh vườn.', 'FAO_IPM', 0.780),
    ('LEAF_BLIGHT', 3, 'Dùng compost tea hoặc chế phẩm sinh học hữu cơ để duy trì sức khỏe đất và tán.', 'Dùng đúng quy trình, tránh làm ướt lá quá lâu sau phun.', 'FAO_IPM', 0.760),
    ('LEAF_BLIGHT', 4, 'Duy trì bón phân hữu cơ cân đối và tránh đạm quá cao trong mùa mưa.', 'Cây quá tốt lá non mềm thường nhạy với bệnh lá hơn.', 'FAO_IPM', 0.790),
    ('LEAF_BLIGHT', 5, 'Kiểm tra nhanh lá mới sau mưa và ghi nhận ổ bệnh để khoanh vùng.', 'Theo dõi 3-5 ngày/lần trong giai đoạn ẩm kéo dài.', 'FAO_IPM', 0.840)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_chemical_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('LEAF_BLIGHT', 1, 'Chỉ dùng propiconazole khi biện pháp vệ sinh và sinh học chưa đủ khống chế bệnh.', 'Phải luân phiên hoạt chất, kiểm tra nhãn durian và PHI trước khi phun.', 'CABI_RHIZOCTONIA_FUNGICIDES', 0.870),
    ('LEAF_BLIGHT', 2, 'Mancozeb hoặc hỗn hợp protectant chỉ nên dùng nếu được đăng ký cho durian tại Việt Nam.', 'Không phun sát thu hoạch; kiểm tra giới hạn dư lượng và lịch cách ly trên nhãn.', 'CABI_RHIZOCTONIA_FUNGICIDES', 0.860)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_active_ingredients (
    ingredient_name,
    chemical_group,
    source_code,
    confidence_level
) VALUES
    ('Propiconazole', 'DMI fungicide', 'CABI_RHIZOCTONIA_FUNGICIDES', 0.900),
    ('Mancozeb', 'Dithiocarbamate', 'CABI_RHIZOCTONIA_FUNGICIDES', 0.900)
ON CONFLICT (ingredient_name) DO NOTHING;

INSERT INTO kb_recommended_chemicals (
    chemical_treatment_id,
    recommendation_order,
    product_name,
    active_ingredient_summary,
    usage_note,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'LEAF_BLIGHT' AND treatment_order = 1),
        1,
        'Propiconazole fungicide',
        'Systemic DMI fungicide used only after sanitation and canopy correction.',
        'Rotate mode of action and verify registration for durian before use.',
        'CABI_RHIZOCTONIA_FUNGICIDES',
        0.880
    ),
    (
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'LEAF_BLIGHT' AND treatment_order = 2),
        1,
        'Mancozeb protectant',
        'Protectant fungicide used as a last-line support in wet periods.',
        'Use only with label approval and keep harvest records.',
        'CABI_RHIZOCTONIA_FUNGICIDES',
        0.860
    )
ON CONFLICT (chemical_treatment_id, recommendation_order) DO NOTHING;

INSERT INTO kb_recommended_chemical_active_ingredients (
    recommended_chemical_id,
    active_ingredient_id,
    sort_order
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Propiconazole fungicide' ORDER BY created_at DESC LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Propiconazole'),
        1
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Mancozeb protectant' ORDER BY created_at DESC LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Mancozeb'),
        1
    )
ON CONFLICT (recommended_chemical_id, active_ingredient_id) DO NOTHING;

INSERT INTO kb_harvest_intervals (
    recommended_chemical_id,
    market_code,
    phi_days,
    notes,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Propiconazole fungicide' ORDER BY created_at DESC LIMIT 1),
        'VN',
        NULL,
        'Verify the exact PHI on the approved durian label before use.',
        'VIETNAM_PPD_LAW',
        0.800
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Mancozeb protectant' ORDER BY created_at DESC LIMIT 1),
        'VN',
        NULL,
        'Use only if the product is registered for durian and the label allows the intended interval.',
        'VIETNAM_PPD_LAW',
        0.780
    )
ON CONFLICT (recommended_chemical_id, market_code) DO NOTHING;

INSERT INTO kb_export_requirements (
    disease_code,
    market_code,
    requirement_order,
    requirement_text,
    warning_text,
    source_code,
    confidence_level
) VALUES
    ('LEAF_BLIGHT', 'VN', 2, 'Keep field spray logs and PHI records for every treated block.', 'Spraying close to harvest increases residue risk.', 'VIETNAM_PPD_LAW', 0.920),
    ('LEAF_BLIGHT', 'EU', 2, 'Verify the destination residue rule for the exact fungicide before shipment.', 'A legal orchard spray may still fail export testing.', 'EU_PESTICIDES_DB', 0.930)
ON CONFLICT (disease_code, market_code, requirement_order) DO NOTHING;

-- ---------------------------------------------------------------------------
-- PHOMOPSIS_LEAF_SPOT
-- ---------------------------------------------------------------------------

INSERT INTO kb_disease_symptoms (
    disease_code,
    symptom_order,
    symptom_text,
    source_code,
    confidence_level
) VALUES
    ('PHOMOPSIS_LEAF_SPOT', 4, 'Vết bệnh nhỏ màu nâu đen hoặc nâu sậm thường có quầng vàng xung quanh.', 'ACTA_PHOMOPSIS_DURIONIS', 0.970),
    ('PHOMOPSIS_LEAF_SPOT', 5, 'Bệnh có thể tồn tại âm thầm trên mô lá và chỉ rõ hơn sau thời gian ủ bệnh.', 'ACTA_PHOMOPSIS_DURIONIS', 0.950),
    ('PHOMOPSIS_LEAF_SPOT', 6, 'Khi bệnh nặng, các chấm đen nhỏ dạng pycnidia có thể xuất hiện trên vết bệnh.', 'ACTA_PHOMOPSIS_DURIONIS', 0.920)
ON CONFLICT (disease_code, symptom_order) DO NOTHING;

INSERT INTO kb_disease_causes (
    disease_code,
    cause_order,
    cause_text,
    source_code,
    confidence_level
) VALUES
    ('PHOMOPSIS_LEAF_SPOT', 2, 'Bào tử có thể phát tán qua mưa bắn và tồn tại âm thầm trên mô cây.', 'ACTA_PHOMOPSIS_DURIONIS', 0.960)
ON CONFLICT (disease_code, cause_order) DO NOTHING;

INSERT INTO kb_biological_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    mechanism,
    source_code,
    confidence_level
) VALUES
    ('PHOMOPSIS_LEAF_SPOT', 1, 'Cắt tỉa lá và cành có triệu chứng, sau đó tiêu hủy khỏi vườn.', 'Giảm nguồn bệnh lưu tồn và hạn chế phát tán bào tử.', 'ACTA_PHOMOPSIS_DURIONIS', 0.940),
    ('PHOMOPSIS_LEAF_SPOT', 2, 'Thu gom và tiêu hủy lá rụng dưới tán để giảm inoculum.', 'Cắt đứt chu kỳ bệnh trong điều kiện ẩm kéo dài.', 'FAO_IPM', 0.920),
    ('PHOMOPSIS_LEAF_SPOT', 3, 'Mở tán để tăng nắng và thông khí, đặc biệt sau các đợt mưa kéo dài.', 'Làm khô bề mặt lá nhanh hơn và giảm tái nhiễm.', 'FAO_IPM', 0.930),
    ('PHOMOPSIS_LEAF_SPOT', 4, 'Tránh làm xây xát lá non và hạn chế phun nước lên tán.', 'Giảm cơ hội xâm nhập và lây lan qua mưa bắn.', 'FAO_IPM', 0.910),
    ('PHOMOPSIS_LEAF_SPOT', 5, 'Dùng Trichoderma spp. hoặc chế phẩm vi sinh đối kháng khi có nguồn kiểm chứng địa phương.', 'Biocontrol hỗ trợ giảm áp lực nấm bệnh trên vật liệu thực vật và đất.', 'PMC_RHIZOCTONIA_BIOCNTROL', 0.840),
    ('PHOMOPSIS_LEAF_SPOT', 6, 'Bổ sung Bacillus spp. hoặc chế phẩm vi sinh có lợi như một phần của IPM.', 'Vi khuẩn có lợi giúp hỗ trợ khống chế bệnh và tăng sức khỏe cây.', 'PMC_RHIZOCTONIA_BIOCNTROL', 0.830)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_organic_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('PHOMOPSIS_LEAF_SPOT', 2, 'Phun neem extract hoặc botanical spray ở giai đoạn đầu của vết bệnh.', 'Chỉ dùng hỗ trợ trong hệ thống IPM và không thay thế tỉa vệ sinh.', 'FAO_IPM', 0.780),
    ('PHOMOPSIS_LEAF_SPOT', 3, 'Dùng garlic extract hoặc hỗn hợp thảo mộc được phép trong mô hình hữu cơ.', 'Theo dõi phản ứng của lá non trước khi phun diện rộng.', 'FAO_IPM', 0.760),
    ('PHOMOPSIS_LEAF_SPOT', 4, 'Bổ sung compost tea hoặc chế phẩm hữu cơ vi sinh để tăng sức khỏe tán và đất.', 'Dùng chế phẩm đã ủ đúng quy trình để tránh phát sinh nấm phụ.', 'FAO_IPM', 0.750),
    ('PHOMOPSIS_LEAF_SPOT', 5, 'Kiểm tra lại lá non và lá trưởng thành sau mỗi đợt mưa lớn hoặc sương kéo dài.', 'Theo dõi dấu hiệu tái nhiễm ở các lá đã có quầng vàng.', 'UC_IPM_PHOMOPSIS', 0.850)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_chemical_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('PHOMOPSIS_LEAF_SPOT', 1, 'Mancozeb hoặc protectant tương tự chỉ nên dùng khi nhãn tại Việt Nam cho phép trên durian.', 'Kiểm tra PHI và không phun sát thu hoạch.', 'UC_IPM_PHOMOPSIS', 0.860),
    ('PHOMOPSIS_LEAF_SPOT', 2, 'Captan hoặc chlorothalonil chỉ dùng khi được đăng ký hợp lệ cho cây trồng và mục tiêu bệnh.', 'Luân phiên hoạt chất, ghi nhật ký phun và xác minh thị trường xuất khẩu.', 'UC_IPM_PHOMOPSIS', 0.850)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_active_ingredients (
    ingredient_name,
    chemical_group,
    source_code,
    confidence_level
) VALUES
    ('Captan', 'Multi-site contact', 'UC_IPM_PHOMOPSIS', 0.880),
    ('Chlorothalonil', 'Multi-site contact', 'UC_IPM_PHOMOPSIS', 0.880)
ON CONFLICT (ingredient_name) DO NOTHING;

INSERT INTO kb_recommended_chemicals (
    chemical_treatment_id,
    recommendation_order,
    product_name,
    active_ingredient_summary,
    usage_note,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'PHOMOPSIS_LEAF_SPOT' AND treatment_order = 1),
        1,
        'Mancozeb protectant',
        'Protectant fungicide used as a defensive option after sanitation.',
        'Use only with verified durian label and PHI.',
        'UC_IPM_PHOMOPSIS',
        0.860
    ),
    (
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'PHOMOPSIS_LEAF_SPOT' AND treatment_order = 2),
        1,
        'Captan / Chlorothalonil rotation',
        'Broad-spectrum contact fungicide rotation for late last-resort use.',
        'Rotate with different mode-of-action products and verify destination residue limits.',
        'UC_IPM_PHOMOPSIS',
        0.850
    )
ON CONFLICT (chemical_treatment_id, recommendation_order) DO NOTHING;

INSERT INTO kb_recommended_chemical_active_ingredients (
    recommended_chemical_id,
    active_ingredient_id,
    sort_order
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Mancozeb protectant' ORDER BY created_at DESC LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Mancozeb'),
        1
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Captan / Chlorothalonil rotation' ORDER BY created_at DESC LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Captan'),
        1
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Captan / Chlorothalonil rotation' ORDER BY created_at DESC LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Chlorothalonil'),
        2
    )
ON CONFLICT (recommended_chemical_id, active_ingredient_id) DO NOTHING;

INSERT INTO kb_harvest_intervals (
    recommended_chemical_id,
    market_code,
    phi_days,
    notes,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Mancozeb protectant' ORDER BY created_at DESC LIMIT 1),
        'VN',
        NULL,
        'PHI must be checked on the approved label for the exact durian product.',
        'VIETNAM_PPD_LAW',
        0.790
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Captan / Chlorothalonil rotation' ORDER BY created_at DESC LIMIT 1),
        'VN',
        NULL,
        'Rotate only when legal for the crop and keep conservative preharvest spacing.',
        'VIETNAM_PPD_LAW',
        0.780
    )
ON CONFLICT (recommended_chemical_id, market_code) DO NOTHING;

INSERT INTO kb_export_requirements (
    disease_code,
    market_code,
    requirement_order,
    requirement_text,
    warning_text,
    source_code,
    confidence_level
) VALUES
    ('PHOMOPSIS_LEAF_SPOT', 'VN', 2, 'Record every spray date, dose, and orchard block in the traceability log.', 'Do not export a treated lot without traceability records.', 'VIETNAM_PPD_LAW', 0.920),
    ('PHOMOPSIS_LEAF_SPOT', 'JP', 2, 'Verify the exact active ingredient and residue rule in the Japan database before packing.', 'Late sprays can block export even when disease is controlled.', 'JAPAN_MRL_DB', 0.930)
ON CONFLICT (disease_code, market_code, requirement_order) DO NOTHING;

-- ---------------------------------------------------------------------------
-- ALLOCARIDARA_ATTACK
-- ---------------------------------------------------------------------------

INSERT INTO kb_disease_symptoms (
    disease_code,
    symptom_order,
    symptom_text,
    source_code,
    confidence_level
) VALUES
    ('ALLOCARIDARA_ATTACK', 4, 'Mật số cao làm xuất hiện mảng sáp trắng và côn trùng non trên đọt non.', 'CABI_DURIAN_PSYLLID', 0.930),
    ('ALLOCARIDARA_ATTACK', 5, 'Lá bị chích hút có thể vàng loang, xoăn và sinh trưởng kém rõ rệt.', 'IJAT_PSYLLID_BASSIANA', 0.890),
    ('ALLOCARIDARA_ATTACK', 6, 'Nhiễm nặng trên đọt non làm chồi phát triển yếu, lá khó bung đều.', 'CABI_MINOR_TROPICAL_FRUITS', 0.880),
    ('ALLOCARIDARA_ATTACK', 7, 'Có thể thấy honeydew hoặc lớp đen do nấm bồ hóng trên bề mặt lá.', 'CABI_DURIAN_PSYLLID', 0.860)
ON CONFLICT (disease_code, symptom_order) DO NOTHING;

INSERT INTO kb_disease_causes (
    disease_code,
    cause_order,
    cause_text,
    source_code,
    confidence_level
) VALUES
    ('ALLOCARIDARA_ATTACK', 2, 'Đợt đọt non ra liên tục và ít thiên địch tự nhiên làm bùng mật số psyllid.', 'IJAT_PSYLLID_BASSIANA', 0.880)
ON CONFLICT (disease_code, cause_order) DO NOTHING;

INSERT INTO kb_biological_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    mechanism,
    source_code,
    confidence_level
) VALUES
    ('ALLOCARIDARA_ATTACK', 2, 'Sử dụng Beauveria bassiana bản địa hoặc chế phẩm nấm ký sinh côn trùng đã được kiểm chứng.', 'Entomopathogenic fungus làm giảm mật số psyllid trên đọt non.', 'BEAUVERIA_PSYLLID', 0.880),
    ('ALLOCARIDARA_ATTACK', 3, 'Sử dụng Metarhizium anisopliae trong chương trình sinh học nếu có nguồn đăng ký và kiểm chứng.', 'Nấm ký sinh côn trùng hỗ trợ quản lý sâu chích hút một cách bền vững.', 'FAO_ENTOMOPATHOGENIC_FUNGI', 0.860),
    ('ALLOCARIDARA_ATTACK', 4, 'Bảo tồn và tăng cường thiên địch bằng cách hạn chế phun phổ rộng không cần thiết.', 'Natural enemies giúp giữ mật số psyllid ở mức thấp hơn.', 'FAO_IPM', 0.900),
    ('ALLOCARIDARA_ATTACK', 5, 'Cắt bỏ đọt bị hại nặng để giảm ổ cư trú và áp lực lây lan trên đợt non.', 'Sanitation trực tiếp giảm nguồn thức ăn và nơi cư trú của côn trùng.', 'CABI_DURIAN_PSYLLID', 0.910),
    ('ALLOCARIDARA_ATTACK', 6, 'Theo dõi và quản lý đợt lộc non đồng bộ để không tạo ổ bùng mật số lớn.', 'Flush management là công cụ sinh học/canh tác cốt lõi với psyllid.', 'IJAT_PSYLLID_BASSIANA', 0.890),
    ('ALLOCARIDARA_ATTACK', 7, 'Áp dụng IPM và kiểm tra đọt non 2-3 ngày/lần trong mùa ra lộc.', 'Scouting dày giúp can thiệp sớm trước khi cần hóa chất.', 'FAO_IPM', 0.900)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_organic_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('ALLOCARIDARA_ATTACK', 2, 'Phun neem extract hoặc botanical insecticidal spray ở giai đoạn mật số thấp.', 'Ưu tiên phun sớm trên đợt non và tránh phun trùng thời điểm cây ra hoa.', 'FAO_IPM', 0.780),
    ('ALLOCARIDARA_ATTACK', 3, 'Dùng garlic extract hoặc hỗn hợp thảo mộc theo mô hình hữu cơ có kiểm chứng.', 'Theo dõi đáp ứng của lá non trước khi áp dụng diện rộng.', 'FAO_IPM', 0.760),
    ('ALLOCARIDARA_ATTACK', 4, 'Đặt sticky traps hoặc light traps để hỗ trợ bẫy và theo dõi mật số trưởng thành.', 'Dùng như công cụ giám sát và giảm áp lực ban đầu, không thay thế theo dõi trực tiếp.', 'FAO_IPM', 0.820),
    ('ALLOCARIDARA_ATTACK', 5, 'Ghi chép đợt lộc và mật số để quyết định thời điểm xử lý tiếp theo.', 'Theo dõi đều đặn giúp giảm nhu cầu phun thuốc hóa học.', 'IJAT_PSYLLID_BASSIANA', 0.860)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_chemical_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('ALLOCARIDARA_ATTACK', 2, 'Chỉ dùng cypermethrin-based insecticide hoặc sản phẩm tương đương khi nhãn tại Việt Nam cho phép.', 'Phải xác minh đăng ký lưu hành, liều, PHI và hạn chế phun sát thu hoạch.', 'IJAT_PSYLLID_BASSIANA', 0.700)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_export_requirements (
    disease_code,
    market_code,
    requirement_order,
    requirement_text,
    warning_text,
    source_code,
    confidence_level
) VALUES
    ('ALLOCARIDARA_ATTACK', 'VN', 2, 'Ghi rõ ngày phun, liều và tên thương mại trong hồ sơ vườn.', 'Pest-treated lots without records are risky for audits.', 'VIETNAM_PPD_LAW', 0.920),
    ('ALLOCARIDARA_ATTACK', 'EU', 2, 'Verify the residue rule for the exact insecticide before export packing.', 'Do not assume field control equals export compliance.', 'EU_PESTICIDES_DB', 0.930)
ON CONFLICT (disease_code, market_code, requirement_order) DO NOTHING;

-- ---------------------------------------------------------------------------
-- HEALTHY_LEAF
-- ---------------------------------------------------------------------------

INSERT INTO kb_disease_symptoms (
    disease_code,
    symptom_order,
    symptom_text,
    source_code,
    confidence_level
) VALUES
    ('HEALTHY_LEAF', 2, 'Lá có màu xanh đồng đều, phiến lá căng và không có đốm hoại tử đáng kể.', 'FAO_IPM', 0.990),
    ('HEALTHY_LEAF', 3, 'Không thấy xoăn lá, cháy mép, đốm loang hay dấu hiệu chích hút rõ rệt.', 'FAO_IPM', 0.980)
ON CONFLICT (disease_code, symptom_order) DO NOTHING;

INSERT INTO kb_disease_causes (
    disease_code,
    cause_order,
    cause_text,
    source_code,
    confidence_level
) VALUES
    ('HEALTHY_LEAF', 2, 'Cây khỏe khi cân bằng dinh dưỡng, tán thông thoáng và IPM được duy trì đều đặn.', 'FAO_IPM', 0.980)
ON CONFLICT (disease_code, cause_order) DO NOTHING;

INSERT INTO kb_biological_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    mechanism,
    source_code,
    confidence_level
) VALUES
    ('HEALTHY_LEAF', 1, 'Duy trì lịch scouting định kỳ trên đọt non và lá già để bảo toàn trạng thái khỏe mạnh.', 'Phát hiện sớm bất thường giúp ngăn chuyển sang bệnh hoặc pest.', 'FAO_IPM', 0.950),
    ('HEALTHY_LEAF', 2, 'Giữ tán thông thoáng và ánh sáng xuyên tán đủ để giảm điều kiện thuận lợi cho bệnh.', 'Tree architecture tốt là nền tảng sinh học của lá khỏe.', 'FAO_SUPERFRUIT_EXPORT', 0.930),
    ('HEALTHY_LEAF', 3, 'Bảo tồn thiên địch và hạn chế phun phổ rộng không cần thiết.', 'IPM tốt giúp giữ cân bằng sinh thái trong vườn.', 'FAO_IPM', 0.940),
    ('HEALTHY_LEAF', 4, 'Tỉa bỏ lá, cành hư hại nhẹ ngay khi thấy để không tạo nguồn bệnh thứ cấp.', 'Sanitation nhẹ duy trì nền vườn sạch và khỏe.', 'FAO_IPM', 0.930),
    ('HEALTHY_LEAF', 5, 'Quản lý ẩm độ đất và thoát nước để bộ rễ hoạt động tốt, từ đó nuôi lá khỏe.', 'Root-zone health phản ánh trực tiếp trên chất lượng lá.', 'FAO_IPM', 0.920)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_organic_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    ('HEALTHY_LEAF', 2, 'Bón phân hữu cơ cân đối và duy trì lớp phủ hữu cơ ổn định quanh vùng rễ.', 'Tránh bón thừa đạm vì lá non mềm dễ mẫn cảm hơn.', 'FAO_IPM', 0.920),
    ('HEALTHY_LEAF', 3, 'Sử dụng compost tea hoặc biofertilizer đã ủ đúng quy trình để hỗ trợ sức khỏe cây.', 'Chỉ dùng sản phẩm an toàn và không làm tăng ẩm kéo dài trên lá.', 'FAO_IPM', 0.800),
    ('HEALTHY_LEAF', 4, 'Duy trì tưới gốc hợp lý và tránh làm ướt lá không cần thiết.', 'Giảm rủi ro cho nấm và tảo phát triển trên bề mặt lá.', 'FAO_IPM', 0.900),
    ('HEALTHY_LEAF', 5, 'Theo dõi lại lá sau mưa lớn hoặc sau điều chỉnh dinh dưỡng để phát hiện sớm stress.', 'Ghi lại ảnh lá để so sánh tình trạng qua các tuần.', 'FAO_SUPERFRUIT_EXPORT', 0.880)
ON CONFLICT (disease_code, treatment_order) DO NOTHING;

INSERT INTO kb_export_requirements (
    disease_code,
    market_code,
    requirement_order,
    requirement_text,
    warning_text,
    source_code,
    confidence_level
) VALUES
    ('HEALTHY_LEAF', 'VN', 2, 'Duy trì nhật ký canh tác và các thao tác canh tác hữu cơ/canh tác tốt.', 'Ngay cả lô khỏe mạnh vẫn cần traceability đầy đủ.', 'VIETGAP_STANDARD', 0.910),
    ('HEALTHY_LEAF', 'EU', 2, 'Giữ hồ sơ đầu vào và kiểm tra lại mọi ứng dụng trước khi thu hoạch.', 'Residue compliance là điều kiện bắt buộc cho export readiness.', 'FAO_SUPERFRUIT_EXPORT', 0.930)
ON CONFLICT (disease_code, market_code, requirement_order) DO NOTHING;

