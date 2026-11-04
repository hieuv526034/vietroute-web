-- Địa điểm thuộc xã/phường; dữ liệu seed là giả lập, giữ ID/audit và snapshot giao dịch.
-- Dừng toàn bộ node và kiểm backup trước chuyển. Địa điểm ngoài map cần xác nhận riêng, không đoán cha.
CREATE TEMP TABLE category_location_wards (
    location_code text PRIMARY KEY, province_code text NOT NULL,
    ward_code text NOT NULL, ward_name text NOT NULL, division_type text NOT NULL
) ON COMMIT DROP;
INSERT INTO category_location_wards VALUES
    ('VRTHUDUC', 'HCM', '26824', 'Phường Thủ Đức', 'phường'),
    ('VRDANANG', 'DANANG', '20200', 'Phường Hòa Khánh', 'phường'),
    ('VRHANOI', 'HANOI', '00592', 'Phường Từ Liêm', 'phường'),
    ('VRHUE', 'HUE', '19789', 'Phường Thuận Hóa', 'phường'),
    ('VRCANTHO', 'CANTHO', '31135', 'Phường Ninh Kiều', 'phường'),
    ('VRDALAT', 'LAMDONG', '24781', 'Phường Xuân Hương - Đà Lạt', 'phường'),
    ('VRNHATRANG', 'KHANHHOA', '22366', 'Phường Nha Trang', 'phường'),
    ('VRQUYNHON', 'GIALAI', '21583', 'Phường Quy Nhơn', 'phường'),
    ('VRBUONMATHUOT', 'DAKLAK', '24133', 'Phường Buôn Ma Thuột', 'phường'),
    ('VRVINH', 'NGHEAN', '16681', 'Phường Thành Vinh', 'phường'),
    ('VRHAIPHONG', 'HAIPHONG', '11383', 'Phường Lê Chân', 'phường'),
    ('VRHALONG', 'QUANGNINH', '06688', 'Phường Hạ Long', 'phường'),
    ('DLGHAIPHONG', 'HAIPHONG', '11383', 'Phường Lê Chân', 'phường'),
    ('DLGHANOI', 'HANOI', '00070', 'Phường Hoàn Kiếm', 'phường');
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM category l
        LEFT JOIN category_location_wards m ON m.location_code=l.code
        LEFT JOIN category p ON p.id=l.parent_id AND p.ref_type='PROVINCE'
        WHERE l.type='VALUE' AND l.ref_type='LOCATION'
            AND (m.location_code IS NULL OR p.code IS DISTINCT FROM m.province_code)
    ) THEN RAISE EXCEPTION 'Location ward mapping requires review; migration rolled back'; END IF;
END $$;
-- Giữ FK bật; chuyển metadata và quan hệ trong cùng transaction trước khi bật lại guard.
ALTER TABLE category DISABLE TRIGGER category_guard;
INSERT INTO category(type,ref_type,code,parent_id,name,props)
SELECT DISTINCT 'VALUE','WARD',m.ward_code,p.id,m.ward_name,
    jsonb_build_object('nameEn',m.ward_name,'administrativeCode',m.ward_code,'divisionType',m.division_type)
FROM category_location_wards m
JOIN category p ON p.type='VALUE' AND p.ref_type='PROVINCE' AND p.code=m.province_code
ON CONFLICT(ref_type,code) DO NOTHING;

-- Chỉ thay khóa cha; giữ mọi nhãn/audit/version của địa điểm cũ.
UPDATE category l SET parent_id=w.id
FROM category_location_wards m
JOIN category w ON w.type='VALUE' AND w.ref_type='WARD' AND w.code=m.ward_code
WHERE l.type='VALUE' AND l.ref_type='LOCATION' AND l.code=m.location_code;
UPDATE category SET props=jsonb_set(props,'{parentRefType}','"WARD"'::jsonb)
WHERE type='GROUP' AND code='LOCATION';
ALTER TABLE category ENABLE TRIGGER category_guard;
-- Đối chiếu quan hệ, giữ trạng thái riêng dù cha đã ngừng sử dụng.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM category l LEFT JOIN category w ON w.id=l.parent_id
        WHERE l.type='VALUE' AND l.ref_type='LOCATION'
            AND (w.id IS NULL OR w.type<>'VALUE' OR w.ref_type<>'WARD')
    ) THEN RAISE EXCEPTION 'Invalid location ward after migration'; END IF;
END $$;

-- Thay tài nguyên đã nghỉ bằng bộ Category của chức năng quản trị hiện hữu.
-- Không gán thêm role/user, bật lại trạng thái hoặc mở rộng quyền các function đọc riêng từng nhóm cũ.
CREATE TEMP TABLE category_retired_paths(path text PRIMARY KEY) ON COMMIT DROP;
INSERT INTO category_retired_paths VALUES
    ('/catalogs/vehicle-types'),
    ('/catalogs/booking-sources'),
    ('/catalogs/provinces'),
    ('/catalogs/locations'),
    ('/operations/locations'),
    ('/api/v1/admin/catalogs'),
    ('/api/v1/admin/catalogs/{id}'),
    ('/api/v1/admin/vehicle-types'),
    ('/api/v1/admin/vehicle-types/{id}'),
    ('/api/v1/admin/booking-sources'),
    ('/api/v1/admin/booking-sources/{id}'),
    ('/api/v1/admin/provinces'),
    ('/api/v1/admin/provinces/{id}'),
    ('/api/v1/admin/locations'),
    ('/api/v1/admin/locations/{id}'),
    ('/api/v1/provinces'),
    ('/api/v1/locations'),
    ('/api/v1/catalogs/{type}');
CREATE TEMP TABLE category_access_before ON COMMIT DROP AS
SELECT f.code,f.version,f.updated_at,f.updated_by,
    COALESCE((SELECT jsonb_agg(r.target ORDER BY r.target) FROM auth_function_resources r
              WHERE r.function_code=f.code),'[]'::jsonb) AS resources
FROM auth_functions f
WHERE f.code='ADMIN_CONSOLE' OR EXISTS (
    SELECT 1 FROM auth_function_resources r JOIN category_retired_paths p ON p.path=r.path_template
    WHERE r.function_code=f.code
);
DELETE FROM auth_function_resources r USING category_retired_paths p WHERE r.path_template=p.path;
INSERT INTO auth_function_resources(function_code,kind,target,http_method,path_template,handler,description,
    name_vi,name_en,icon,sort_order)
SELECT f.code,x.kind,x.target,x.method,x.path,'Category migration',x.name_vi,x.name_vi,x.name_en,'catalog',x.sort_order
FROM auth_functions f
CROSS JOIN (VALUES
    ('MENU', 'GET /operations/categories', 'GET', '/operations/categories', 'Danh mục chung', 'Categories', 0),
    ('ENDPOINT', 'GET /api/v1/admin/categories/groups', 'GET', '/api/v1/admin/categories/groups', 'Đọc nhóm danh mục', 'Read category groups', 1),
    ('ENDPOINT', 'GET /api/v1/admin/categories', 'GET', '/api/v1/admin/categories', 'Đọc mục theo cha', 'Read category items by parent', 2),
    ('ENDPOINT', 'GET /api/v1/admin/categories/{id}', 'GET', '/api/v1/admin/categories/{id}', 'Chi tiết danh mục', 'Category detail', 3),
    ('ENDPOINT', 'POST /api/v1/admin/categories', 'POST', '/api/v1/admin/categories', 'Tạo danh mục', 'Create category', 4),
    ('ENDPOINT', 'PUT /api/v1/admin/categories/{id}', 'PUT', '/api/v1/admin/categories/{id}', 'Sửa danh mục', 'Update category', 5),
    ('ENDPOINT', 'DELETE /api/v1/admin/categories/{id}', 'DELETE', '/api/v1/admin/categories/{id}', 'Ngừng dùng danh mục', 'Disable category', 6)
) x(kind,target,method,path,name_vi,name_en,sort_order)
WHERE f.code='ADMIN_CONSOLE'
ON CONFLICT(function_code,kind,target) DO NOTHING;
CREATE TEMP TABLE category_access_after ON COMMIT DROP AS
SELECT f.code,COALESCE((SELECT jsonb_agg(r.target ORDER BY r.target) FROM auth_function_resources r
    WHERE r.function_code=f.code),'[]'::jsonb) AS resources
FROM auth_functions f JOIN category_access_before b ON b.code=f.code;
UPDATE auth_functions f SET version=f.version+1,updated_at=CURRENT_TIMESTAMP,updated_by=0
FROM category_access_before b JOIN category_access_after a USING(code)
WHERE f.code=b.code AND a.resources IS DISTINCT FROM b.resources;
INSERT INTO auth_access_changes(event_type,target_kind,target_key,actor_id,before_state,after_state)
SELECT 'CATEGORY_RESOURCES_MIGRATED','FUNCTION',b.code,0,
    jsonb_build_object('mappingCount',jsonb_array_length(b.resources),'resources',b.resources,'version',b.version),
    jsonb_build_object('mappingCount',jsonb_array_length(a.resources),'resources',a.resources,'version',b.version+1)
FROM category_access_before b JOIN category_access_after a USING(code)
WHERE a.resources IS DISTINCT FROM b.resources;
