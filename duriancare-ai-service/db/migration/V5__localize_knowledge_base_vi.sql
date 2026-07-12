-- Localize core disease summaries and favorable conditions for Vietnamese end users.
-- This migration keeps the existing schema and identifiers unchanged.

UPDATE kb_diseases
SET
    disease_summary = 'Bệnh đốm mắt cua là bệnh hại lá do tảo ký sinh Cephaleuros virescens gây ra. Bệnh thường phát triển mạnh trong điều kiện mưa nhiều, ẩm độ cao, tán cây rậm và lá ướt kéo dài; nếu lan rộng sẽ làm giảm quang hợp và suy yếu cây.',
    favorable_conditions = 'Thời tiết nóng ẩm, mùa mưa, tán cây rậm và độ ẩm lá cao kéo dài.'
WHERE code = 'ALGAL_LEAF_SPOT';

UPDATE kb_diseases
SET
    disease_summary = 'Bệnh cháy lá là bệnh hại lá do nấm Rhizoctonia solani AG1-ID gây ra. Bệnh thường bùng phát khi mưa ẩm kéo dài, tán cây rậm và lá ướt lâu; nếu không xử lý sớm, lá sẽ cháy khô, héo và rụng hàng loạt.',
    favorable_conditions = 'Mùa mưa, ẩm độ cao, vườn thoát nước kém, tán rậm và lá ướt lâu.'
WHERE code = 'LEAF_BLIGHT';

UPDATE kb_diseases
SET
    disease_summary = 'Bệnh đốm lá Phomopsis là bệnh hại lá do nấm Phomopsis durionis gây ra. Bệnh thường khởi phát âm thầm với các đốm nhỏ màu nâu đen, sau đó lan rộng nhanh trong điều kiện nóng ẩm và mưa tạt; nếu nặng có thể làm giảm chất lượng tán lá.',
    favorable_conditions = 'Thời tiết nóng ẩm, mưa nhiều, tán dày, nhiễm tiềm ẩn trong mô cây và phát tán theo mưa tạt.'
WHERE code = 'PHOMOPSIS_LEAF_SPOT';

UPDATE kb_diseases
SET
    disease_summary = 'Bọ chích hút lá sầu riêng là đối tượng chích hút nhựa non do Allocaridara malayensis gây ra. Côn trùng này thường làm lá non xoăn, vàng lá, chậm sinh trưởng và khiến đợt lộc non suy yếu rõ rệt.',
    favorable_conditions = 'Ra lộc liên tục, ít thiên địch, tán non dày và giám sát vườn chưa chặt.'
WHERE code = 'ALLOCARIDARA_ATTACK';

UPDATE kb_diseases
SET
    disease_summary = 'Lá khỏe là trạng thái lá có màu xanh đồng đều, không đốm bệnh, không xoăn, không cháy mép và không có dấu hiệu chích hút; đây là mục tiêu quản lý của vườn sầu riêng.',
    favorable_conditions = 'Dinh dưỡng cân bằng, tán thông thoáng, vệ sinh vườn tốt và giám sát IPM đều đặn.'
WHERE code = 'HEALTHY_LEAF';
