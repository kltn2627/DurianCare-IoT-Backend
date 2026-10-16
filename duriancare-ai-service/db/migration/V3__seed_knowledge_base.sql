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
        'FAO_IPM',
        'Food and Agriculture Organization of the United Nations',
        'OFFICIAL',
        'Integrated Pest Management',
        'FAO',
        2025,
        'https://www.fao.org/pest-and-pesticide-management/ipm/integrated-pest-management/en/',
        0.990,
        'General IPM principle source for sustainable agriculture and least-disruptive control.'
    ),
    (
        'FAO_CODEX_MRL',
        'Codex Alimentarius / FAO-WHO',
        'DATABASE',
        'Codex Pesticide Residue Database',
        'FAO-WHO Codex',
        2025,
        'https://www.fao.org/fao-who-codexalimentarius/codex-texts/dbs/pestres/en/',
        0.980,
        'Reference database for MRL lookup.'
    ),
    (
        'EU_PESTICIDES_DB',
        'European Commission',
        'DATABASE',
        'EU Pesticides Database',
        'European Commission',
        2026,
        'https://food.ec.europa.eu/plants/pesticides/eu-pesticides-database_en',
        0.970,
        'Destination-market MRL lookup for EU shipments.'
    ),
    (
        'JAPAN_MRL_DB',
        'Japan Food Chemical Research Foundation',
        'DATABASE',
        'Residue Limits of Agricultural Chemicals',
        'Japan Food Chemical Research Foundation',
        2026,
        'https://www.ffcr.or.jp/en/zanryu/',
        0.970,
        'Destination-market MRL lookup for Japan.'
    ),
    (
        'VIETNAM_PPD_LAW',
        'Vietnam Plant Protection and Quarantine authority reference',
        'OFFICIAL',
        'Law on Plant Protection and Quarantine / pesticide registration references',
        'Vietnam Ministry of Agriculture and Environment',
        2024,
        'https://bwcimplementation.org/sites/default/files/resource/VD_Law%20on%20Plant%20Protection%20and%20Quarantine_EN.pdf',
        0.920,
        'Use to validate legal use and registered products in Vietnam.'
    ),
    (
        'GLOBALGAP_IFA',
        'GLOBALG.A.P.',
        'STANDARD',
        'Integrated Farm Assurance',
        'GLOBALG.A.P.',
        2019,
        'https://documents.globalgap.org/documents/190201_GG_IFA_CPCC_CC_V5_2_en.pdf',
        0.930,
        'Residue discipline and farm assurance reference.'
    ),
    (
        'VIETGAP_STANDARD',
        'Vietnamese Good Agricultural Practices',
        'STANDARD',
        'VietGAP standard and certification references',
        'Ministry of Agriculture and Rural Development / Vietnam MAE',
        2025,
        'https://en.mae.gov.vn/viet-nam-has-over-8300-vietgapcertified-facilities-and-2000-safe-agricultural-supply-chains-9134.htm',
        0.920,
        'Use for domestic safety assurance and traceability guidance.'
    ),
    (
        'CABI_CEPHALEUROS_VIRESCENS',
        'CABI Compendium',
        'DATABASE',
        'Cephaleuros virescens',
        'CABI',
        2026,
        'https://www.cabidigitallibrary.org/doi/full/10.1079/cabicompendium.12111',
        0.970,
        'Algal leaf spot identity and host reference.'
    ),
    (
        'SPRINGER_CEPHALEUROS_DURIAN',
        'Australasian Plant Disease Notes',
        'PEER_REVIEWED',
        'Cephaleuros virescens, the cause of an algal leaf spot on Para rubber in Thailand',
        'Springer',
        2015,
        'https://link.springer.com/article/10.1007/s13314-015-0158-1',
        0.900,
        'Supports symptoms and organism behavior for Cephaleuros leaf spot.'
    ),
    (
        'HGIC_ALGAL_LEAF_SPOT',
        'Clemson HGIC',
        'EXTENSION',
        'Algal Leaf Spot',
        'Clemson Cooperative Extension',
        2024,
        'https://hgic.clemson.edu/factsheet/algal-leaf-spot/',
        0.860,
        'Conservative control guidance for severe algal leaf spot.'
    ),
    (
        'PUBMED_RHIZOCTONIA_DURIAN',
        'Plant Disease / PubMed',
        'PEER_REVIEWED',
        'First Report of Rhizoctonia solani Subgroup AG 1-ID Causing Leaf Blight on Durian in Vietnam',
        'American Phytopathological Society',
        2019,
        'https://pubmed.ncbi.nlm.nih.gov/30769621/',
        0.980,
        'Direct durian leaf blight evidence from Vietnam.'
    ),
    (
        'SPCHCMC_LEAF_BLIGHT',
        'Sai Gon - Ho Chi Minh City plant doctor reference',
        'OFFICIAL',
        'Prevention of Leaf Blight Disease',
        'SPCHCMC',
        2021,
        'https://www.spchcmc.vn/EN/Plant-doctor-Detail/Prevention-Of-Leaf-Blight-Disease-5-8401.html',
        0.860,
        'Used for pruning and ventilation advice.'
    ),
    (
        'ISHS_LEAF_ASSAY',
        'International Society for Horticultural Science',
        'PEER_REVIEWED',
        'Leaf assay screening antagonistic microorganisms to control leaf web blight of durian',
        'ISHS',
        2006,
        'https://ishs.org/ishs-article/1024_40/',
        0.860,
        'Supports biological control direction for Rhizoctonia leaf blight.'
    ),
    (
        'ACTA_PHOMOPSIS_DURIONIS',
        'Acta Universitatis Agriculturae et Silviculturae Mendelianae Brunensis',
        'PEER_REVIEWED',
        'Leaf Spot Characteristics of Phomopsis Durionis on Durian',
        'Mendel University',
        2016,
        'https://actavia.mendelu.cz/pdfs/acu/2016/01/22.pdf',
        0.970,
        'Direct symptom and latent infection evidence for Phomopsis leaf spot.'
    ),
    (
        'CABI_DURIAN_PSYLLID',
        'CABI ISC Datasheet',
        'DATABASE',
        'Allocaridara malayensis',
        'CABI',
        2019,
        'https://www.cabi.org/isc/datasheet/4299',
        0.950,
        'Durian psyllid identity and pest status.'
    ),
    (
        'CABI_MINOR_TROPICAL_FRUITS',
        'CABI Digital Library',
        'DATABASE',
        '10 Pests of Minor Tropical Fruits',
        'CABI',
        2001,
        'https://www.cabidigitallibrary.org/doi/pdf/10.5555/20073012690',
        0.900,
        'Supports durian psyllid severity as a major pest.'
    ),
    (
        'IJAT_PSYLLID_BASSIANA',
        'International Journal of Agricultural Technology',
        'PEER_REVIEWED',
        'The management practice methods for Allocarsidara malayensis Crawford by spraying insecticide with AI drone and long hose pump sprayers in durian orchards',
        'IJAT',
        2024,
        'https://www.ijat-aatsea.com/pdf/v20_n4_2024_July/30_IJAT_20%284%29_2024_Wiangsamut%2C%20B.--1640.pdf',
        0.880,
        'Used for cypermethrin efficacy and pest management context.'
    ),
    (
        'BEAUVERIA_PSYLLID',
        'ResearchGate linked study / peer-reviewed source trace',
        'PEER_REVIEWED',
        'Effect of Indigenous Beauveria bassiana on the Control of Allocaridala malayensis',
        'Research article trace',
        2024,
        'https://www.researchgate.net/publication/376857493_Effect_of_Indigenous_Beauveria_bassiana_on_the_Control_of_Allocaridala_maleyensis_Crawford_in_durian_plantation_areas_in_eastern_region',
        0.760,
        'Biological control evidence for durian psyllid management.'
    );

INSERT INTO kb_export_markets (
    market_code,
    market_name,
    authority_name,
    guidance_url,
    notes
) VALUES
    ('VN', 'Vietnam', 'Vietnam Ministry of Agriculture and Environment / Plant Protection Department', 'https://www.fao.org/4/ag123e/AG123E18.htm', 'Use only legally registered plant protection products and follow local label directions.'),
    ('CN', 'China', 'China MRL system / SPS notifications', 'https://apps.fas.usda.gov/newgainapi/api/Report/DownloadReportByFileName?fileName=China+Notifies+Additional+Maximum+Residue+Limits+for+Pesticides+in+Foods+to+the+WTO+_Beijing_China+-+People%27s+Republic+of_CH2026-0004.pdf', 'Check the latest China residue list before shipment.'),
    ('EU', 'European Union', 'European Commission', 'https://food.ec.europa.eu/plants/pesticides/eu-pesticides-database_en', 'Verify destination-specific MRLs in the EU database.'),
    ('JP', 'Japan', 'Japan Food Chemical Research Foundation', 'https://www.ffcr.or.jp/en/zanryu/', 'Verify agricultural chemical residue limits in the Japanese database.');

INSERT INTO kb_diseases (
    code,
    vietnamese_name,
    english_name,
    scientific_name,
    issue_type,
    severity,
    disease_summary,
    favorable_conditions,
    confidence_level,
    primary_source_code
) VALUES
    (
        'ALGAL_LEAF_SPOT',
        'Bệnh đốm mắt cua',
        'Algal leaf spot',
        'Cephaleuros virescens',
        'DISEASE',
        'MEDIUM',
        'Đốm nâu cam đến đỏ nâu trên lá, bề mặt có thể hơi sần hoặc nhung; làm giảm quang hợp nếu lan rộng.',
        'Thời tiết nóng ẩm, mùa mưa, tán cây rậm và độ ẩm lá cao kéo dài.',
        0.950,
        'SPRINGER_CEPHALEUROS_DURIAN'
    ),
    (
        'LEAF_BLIGHT',
        'Bệnh cháy lá',
        'Leaf blight',
        'Rhizoctonia solani AG1-ID',
        'DISEASE',
        'HIGH',
        'Vết cháy lớn màu nâu nhạt, mép không đều; nặng có thể làm lá khô, héo và rụng.',
        'Mùa mưa, ẩm độ cao, vườn thoáng kém, lá dễ bị ướt lâu.',
        0.970,
        'PUBMED_RHIZOCTONIA_DURIAN'
    ),
    (
        'PHOMOPSIS_LEAF_SPOT',
        'Bệnh đốm lá Phomopsis',
        'Phomopsis leaf spot',
        'Phomopsis durionis',
        'DISEASE',
        'MEDIUM',
        'Đốm nhỏ màu nâu đen với quầng vàng trên lá non và lá trưởng thành; có thể xuất hiện nhiễm tiềm ẩn.',
        'Vườn quản lý kém, mưa nhiều, ẩm cao, tán lá dày và ít thông thoáng.',
        0.950,
        'ACTA_PHOMOPSIS_DURIONIS'
    ),
    (
        'ALLOCARIDARA_ATTACK',
        'Bọ chích hút lá sầu riêng',
        'Durian psyllid attack',
        'Allocaridara malayensis',
        'PEST',
        'HIGH',
        'Ấu trùng và trưởng thành hút nhựa chồi/lá non, tạo quần thể sáp trắng, đốm vàng và hiện tượng quăn lá.',
        'Đọt non ra mạnh, mật số cao, vườn ít theo dõi hoặc ít thiên địch tự nhiên.',
        0.940,
        'CABI_DURIAN_PSYLLID'
    ),
    (
        'HEALTHY_LEAF',
        'Lá khỏe',
        'Healthy leaf',
        NULL,
        'HEALTHY',
        'LOW',
        'Không ghi nhận triệu chứng bệnh hoặc tổn thương đáng kể.',
        'Cân bằng dinh dưỡng, tán thông thoáng và quản lý IPM tốt.',
        1.000,
        'FAO_IPM'
    );

INSERT INTO kb_disease_symptoms (
    disease_code,
    symptom_order,
    symptom_text,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 1, 'Đốm tròn hoặc loang màu nâu cam đến đỏ nâu trên lá.', 'CABI_CEPHALEUROS_VIRESCENS', 0.950),
    ('ALGAL_LEAF_SPOT', 2, 'Bề mặt tổn thương có thể hơi sần, nhung hoặc crusty.', 'HGIC_ALGAL_LEAF_SPOT', 0.880),
    ('ALGAL_LEAF_SPOT', 3, 'Bệnh có thể thấy trên lá, cành non và đôi khi trên quả.', 'HGIC_ALGAL_LEAF_SPOT', 0.870),
    ('LEAF_BLIGHT', 1, 'Mảng cháy lớn màu nâu nhạt, ranh giới không đều.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.970),
    ('LEAF_BLIGHT', 2, 'Trong ẩm độ cao có thể thấy sợi nấm trắng vàng trên vết bệnh.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.920),
    ('LEAF_BLIGHT', 3, 'Lá bị nặng chuyển nâu sẫm, héo và khô nhanh.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.930),
    ('PHOMOPSIS_LEAF_SPOT', 1, 'Đốm nâu đen rất nhỏ, thường có quầng vàng.', 'ACTA_PHOMOPSIS_DURIONIS', 0.970),
    ('PHOMOPSIS_LEAF_SPOT', 2, 'Vết bệnh xuất hiện trên cả lá non và lá trưởng thành.', 'ACTA_PHOMOPSIS_DURIONIS', 0.950),
    ('PHOMOPSIS_LEAF_SPOT', 3, 'Trường hợp nặng có thể thấy chấm đen nhỏ kiểu pycnidia trên tổn thương.', 'ACTA_PHOMOPSIS_DURIONIS', 0.920),
    ('ALLOCARIDARA_ATTACK', 1, 'Lá non quăn hoặc biến dạng do chích hút.', 'CABI_DURIAN_PSYLLID', 0.930),
    ('ALLOCARIDARA_ATTACK', 2, 'Xuất hiện dịch sáp trắng và cụm côn trùng non/trưởng thành trên đọt non.', 'CABI_DURIAN_PSYLLID', 0.920),
    ('ALLOCARIDARA_ATTACK', 3, 'Lá bị chích hút có thể xuất hiện đốm vàng và sinh trưởng kém.', 'IJAT_PSYLLID_BASSIANA', 0.880),
    ('HEALTHY_LEAF', 1, 'Phiến lá xanh đều, không có đốm bệnh hoặc biến dạng đáng kể.', 'FAO_IPM', 0.990);

INSERT INTO kb_disease_causes (
    disease_code,
    cause_order,
    cause_text,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 1, 'Cephaleuros virescens là tảo ký sinh gây đốm mắt cua trên lá.', 'CABI_CEPHALEUROS_VIRESCENS', 0.980),
    ('LEAF_BLIGHT', 1, 'Rhizoctonia solani AG1-ID là tác nhân gây cháy lá trên sầu riêng tại Việt Nam.', 'PUBMED_RHIZOCTONIA_DURIAN', 0.990),
    ('PHOMOPSIS_LEAF_SPOT', 1, 'Phomopsis durionis là tác nhân gây bệnh đốm lá Phomopsis trên sầu riêng.', 'ACTA_PHOMOPSIS_DURIONIS', 0.980),
    ('ALLOCARIDARA_ATTACK', 1, 'Allocaridara malayensis là loài psyllid chích hút chồi và lá non.', 'CABI_DURIAN_PSYLLID', 0.980),
    ('HEALTHY_LEAF', 1, 'Không có tác nhân gây bệnh; đây là trạng thái lá khỏe mạnh.', 'FAO_IPM', 1.000);

INSERT INTO kb_biological_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    mechanism,
    source_code,
    confidence_level
) VALUES
    (
        'LEAF_BLIGHT',
        1,
        'Ưu tiên tác nhân đối kháng đã được sàng lọc trong thử nghiệm địa phương để hướng tới kiểm soát sinh học bền vững.',
        'Biocontrol bằng vi sinh vật đối kháng là hướng giảm phụ thuộc hóa chất.',
        'ISHS_LEAF_ASSAY',
        0.780
    ),
    (
        'ALLOCARIDARA_ATTACK',
        1,
        'Sử dụng Beauveria bassiana bản địa hoặc các chế phẩm nấm ký sinh côn trùng đã được kiểm chứng trong nghiên cứu địa phương.',
        'Nấm đối kháng côn trùng có thể làm giảm mật số psyllid trên đọt non.',
        'BEAUVERIA_PSYLLID',
        0.760
    );

INSERT INTO kb_organic_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    (
        'ALGAL_LEAF_SPOT',
        1,
        'Tỉa tán để tăng thông thoáng, giảm ẩm độ kéo dài trên lá.',
        'Cắt bỏ phần tán dày giúp lá khô nhanh và giảm điều kiện thuận lợi cho đốm tảo.',
        'FAO_IPM',
        0.920
    ),
    (
        'LEAF_BLIGHT',
        1,
        'Không trồng quá dày; tỉa cành kém hiệu quả vào đầu mùa mưa để tăng ánh sáng và thông gió.',
        'Vệ sinh vườn và giữ tán thoáng là nền tảng kiểm soát bền vững.',
        'SPCHCMC_LEAF_BLIGHT',
        0.920
    ),
    (
        'PHOMOPSIS_LEAF_SPOT',
        1,
        'Thu gom và loại bỏ lá bệnh, hạn chế nguồn bệnh lưu tồn.',
        'Không để lá bệnh rơi tích tụ dưới tán.',
        'ACTA_PHOMOPSIS_DURIONIS',
        0.900
    ),
    (
        'ALLOCARIDARA_ATTACK',
        1,
        'Theo dõi đọt non thường xuyên, cắt bỏ đọt bị hại nặng và bảo tồn thiên địch.',
        'Quản lý mật số sớm giúp giảm nhu cầu phun thuốc.',
        'FAO_IPM',
        0.900
    ),
    (
        'HEALTHY_LEAF',
        1,
        'Duy trì cân bằng dinh dưỡng, tưới tiêu hợp lý và giám sát vườn định kỳ.',
        'Lá khỏe là kết quả của quản lý IPM liên tục.',
        'FAO_IPM',
        0.960
    );

INSERT INTO kb_chemical_treatments (
    disease_code,
    treatment_order,
    treatment_text,
    safe_usage_note,
    source_code,
    confidence_level
) VALUES
    (
        'ALGAL_LEAF_SPOT',
        1,
        'Chỉ dùng thuốc gốc đồng khi bệnh nặng và điều kiện ẩm kéo dài.',
        'Phun đúng nhãn, không lạm dụng, và kiểm tra thời gian cách ly trước thu hoạch.',
        'HGIC_ALGAL_LEAF_SPOT',
        0.840
    ),
    (
        'ALLOCARIDARA_ATTACK',
        1,
        'Có thể dùng cypermethrin theo nghiên cứu kiểm soát psyllid trên đọt non nếu sản phẩm được phép lưu hành.',
        'Phải xác minh nhãn đăng ký tại Việt Nam, liều dùng thực tế và PHI trước khi áp dụng ngoài đồng.',
        'IJAT_PSYLLID_BASSIANA',
        0.700
    );

INSERT INTO kb_active_ingredients (
    ingredient_name,
    chemical_group,
    source_code,
    confidence_level
) VALUES
    (
        'Copper oxychloride',
        'Copper compound',
        'HGIC_ALGAL_LEAF_SPOT',
        0.820
    ),
    (
        'Cypermethrin',
        'Pyrethroid',
        'IJAT_PSYLLID_BASSIANA',
        0.900
    );

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
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'ALGAL_LEAF_SPOT' AND treatment_order = 1),
        1,
        'Copper fungicide',
        'Copper-based fungicide family used against severe algal leaf spot in wet periods.',
        'Use only if the label and local regulation allow copper on durian.',
        'HGIC_ALGAL_LEAF_SPOT',
        0.820
    ),
    (
        (SELECT id FROM kb_chemical_treatments WHERE disease_code = 'ALLOCARIDARA_ATTACK' AND treatment_order = 1),
        1,
        'Cypermethrin-based insecticide',
        'Pyrethroid insecticide used as a research-backed control option for durian psyllid.',
        'Recheck product registration, application interval, and harvest interval before field use.',
        'IJAT_PSYLLID_BASSIANA',
        0.700
    );

INSERT INTO kb_recommended_chemical_active_ingredients (
    recommended_chemical_id,
    active_ingredient_id,
    sort_order
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Copper fungicide' ORDER BY created_at LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Copper oxychloride'),
        1
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Cypermethrin-based insecticide' ORDER BY created_at LIMIT 1),
        (SELECT id FROM kb_active_ingredients WHERE ingredient_name = 'Cypermethrin'),
        1
    );

INSERT INTO kb_harvest_intervals (
    recommended_chemical_id,
    market_code,
    phi_days,
    notes,
    source_code,
    confidence_level
) VALUES
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Copper fungicide' ORDER BY created_at LIMIT 1),
        'VN',
        NULL,
        'Durian-specific PHI must be checked against the approved label before use.',
        'VIETNAM_PPD_LAW',
        0.800
    ),
    (
        (SELECT id FROM kb_recommended_chemicals WHERE product_name = 'Cypermethrin-based insecticide' ORDER BY created_at LIMIT 1),
        'VN',
        NULL,
        'Research-backed use only. Verify Vietnam registration and PHI for the exact product label.',
        'VIETNAM_PPD_LAW',
        0.750
    );

INSERT INTO kb_export_requirements (
    disease_code,
    market_code,
    requirement_order,
    requirement_text,
    warning_text,
    source_code,
    confidence_level
) VALUES
    ('ALGAL_LEAF_SPOT', 'VN', 1, 'Apply only legally registered products and keep the label/PHI in farm records.', 'Copper residue risk rises when wet-season sprays are repeated without label discipline.', 'VIETNAM_PPD_LAW', 0.900),
    ('ALGAL_LEAF_SPOT', 'CN', 1, 'Check the current China MRL list for any active ingredient used in control.', 'Do not assume an active ingredient acceptable in Vietnam is automatically accepted in China.', 'FAO_CODEX_MRL', 0.880),
    ('ALGAL_LEAF_SPOT', 'EU', 1, 'Verify the commodity-specific limit in the EU Pesticides Database before shipment.', 'Use traceability records to prove residue compliance.', 'EU_PESTICIDES_DB', 0.930),
    ('ALGAL_LEAF_SPOT', 'JP', 1, 'Check the Japan residue limit database for every active ingredient used.', 'Japan residue controls are strict; manual verification is required before export.', 'JAPAN_MRL_DB', 0.930),

    ('LEAF_BLIGHT', 'VN', 1, 'Prefer sanitation and canopy management; if fungicide is needed, keep the label and PHI records.', 'Repeated fungicide use without documentation increases residue and audit risk.', 'VIETNAM_PPD_LAW', 0.900),
    ('LEAF_BLIGHT', 'CN', 1, 'Confirm the current MRL position for any fungicide used in the control program.', 'Export lots should be backed by residue logs and batch traceability.', 'FAO_CODEX_MRL', 0.880),
    ('LEAF_BLIGHT', 'EU', 1, 'Use destination-specific MRL lookup before packing the harvest lot.', 'A product legal in the orchard may still fail EU residue checks.', 'EU_PESTICIDES_DB', 0.930),
    ('LEAF_BLIGHT', 'JP', 1, 'Validate residue limits in the Japan database before export.', 'Keep the harvest interval conservative when the control program uses fungicides.', 'JAPAN_MRL_DB', 0.930),

    ('PHOMOPSIS_LEAF_SPOT', 'VN', 1, 'Prioritize pruning, sanitation and traceability; document any chemical use if added later.', 'Chemical use should stay exceptional and label-driven.', 'FAO_IPM', 0.900),
    ('PHOMOPSIS_LEAF_SPOT', 'CN', 1, 'Check the China MRL database for any active ingredient associated with the crop lot.', 'Do not export a treated lot without residue review.', 'FAO_CODEX_MRL', 0.880),
    ('PHOMOPSIS_LEAF_SPOT', 'EU', 1, 'Use EU MRL lookup and batch residue testing for export readiness.', 'Traceability records should preserve the spray history.', 'EU_PESTICIDES_DB', 0.930),
    ('PHOMOPSIS_LEAF_SPOT', 'JP', 1, 'Use Japan residue references and document all orchard inputs.', 'Export approval depends on residue discipline, not just disease status.', 'JAPAN_MRL_DB', 0.930),

    ('ALLOCARIDARA_ATTACK', 'VN', 1, 'Use only registered insecticides and keep exact PHI records if chemical control is applied.', 'Insecticide residue can directly block export if PHI is ignored.', 'VIETNAM_PPD_LAW', 0.920),
    ('ALLOCARIDARA_ATTACK', 'CN', 1, 'Verify the active ingredient against the current China residue list.', 'Young-leaf spraying should be carefully documented for export lots.', 'FAO_CODEX_MRL', 0.880),
    ('ALLOCARIDARA_ATTACK', 'EU', 1, 'Check the EU Pesticides Database for the crop and active ingredient before shipment.', 'High residue risk if repeated sprays are used without label control.', 'EU_PESTICIDES_DB', 0.930),
    ('ALLOCARIDARA_ATTACK', 'JP', 1, 'Use the Japan MRL database and label instructions as the final gate.', 'Strict residue verification is required for insecticide-treated lots.', 'JAPAN_MRL_DB', 0.930);
