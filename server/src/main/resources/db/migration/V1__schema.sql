create table carrier (
  id bigint not null auto_increment primary key,
  carrier_code varchar(32) not null,
  carrier_name varchar(128) not null,
  webhook_secret varchar(255) not null,
  query_base_url varchar(255),
  enabled boolean not null default true,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_carrier_code unique(carrier_code)
);

create table carrier_status_mapping (
  id bigint not null auto_increment primary key,
  carrier_id bigint not null,
  raw_status varchar(64) not null,
  normalized_status varchar(64) not null,
  enabled boolean not null default true,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_mapping unique(carrier_id, raw_status),
  constraint fk_mapping_carrier foreign key(carrier_id) references carrier(id)
);

create table shipment (
  id bigint not null auto_increment primary key,
  tracking_no varchar(64) not null,
  carrier_id bigint not null,
  business_order_no varchar(64),
  current_status varchar(64) not null,
  current_status_event_time datetime(6),
  last_received_time datetime(6),
  max_event_time datetime(6),
  has_open_anomaly boolean not null default false,
  version int not null default 0,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_shipment unique(carrier_id, tracking_no),
  constraint fk_shipment_carrier foreign key(carrier_id) references carrier(id)
);

create index idx_shipment_status_updated on shipment(current_status, updated_at);

create table raw_carrier_event (
  id bigint not null auto_increment primary key,
  carrier_id bigint not null,
  shipment_id bigint not null,
  tracking_no varchar(64) not null,
  external_event_id varchar(128),
  idempotency_key varchar(128) not null,
  event_fingerprint varchar(128) not null,
  raw_status varchar(64) not null,
  raw_body text not null,
  request_headers text,
  signature_valid boolean not null,
  received_time datetime(6) default current_timestamp(6),
  process_status varchar(32) not null,
  failure_reason varchar(500),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_raw_idempotency unique(carrier_id, idempotency_key),
  constraint fk_raw_carrier foreign key(carrier_id) references carrier(id),
  constraint fk_raw_shipment foreign key(shipment_id) references shipment(id)
);

create index idx_raw_tracking_time on raw_carrier_event(tracking_no, received_time);
create index idx_raw_process_created on raw_carrier_event(process_status, created_at);

create table normalized_event (
  id bigint not null auto_increment primary key,
  shipment_id bigint not null,
  carrier_id bigint not null,
  raw_event_id bigint,
  external_event_id varchar(128),
  normalized_status varchar(64) not null,
  raw_status varchar(64) not null,
  event_time datetime(6) not null,
  received_time datetime(6) not null,
  location varchar(255),
  description varchar(500),
  source varchar(32) not null,
  late_arrival boolean not null,
  applied_to_state boolean not null,
  validation_status varchar(32) not null,
  event_fingerprint varchar(128) not null,
  created_at datetime(6) not null,
  constraint uk_normalized_raw unique(raw_event_id),
  constraint uk_normalized_carrier_fingerprint unique(carrier_id, event_fingerprint),
  constraint fk_ne_shipment foreign key(shipment_id) references shipment(id),
  constraint fk_ne_carrier foreign key(carrier_id) references carrier(id)
);

create index idx_ne_timeline on normalized_event(shipment_id, event_time, received_time, id);

create table event_process_task (
  id bigint not null auto_increment primary key,
  raw_event_id bigint not null,
  task_key varchar(128) not null,
  status varchar(32) not null,
  retry_count int not null,
  max_retry_count int not null,
  next_retry_time datetime(6),
  locked_at datetime(6),
  locked_by varchar(64),
  message_sent_at datetime(6),
  last_error varchar(500),
  finished_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_task_key unique(task_key),
  constraint fk_task_raw foreign key(raw_event_id) references raw_carrier_event(id)
);

create index idx_task_status_next on event_process_task(status, next_retry_time);

create table outbox_event (
  id bigint not null auto_increment primary key,
  event_key varchar(160) not null,
  aggregate_type varchar(64) not null,
  aggregate_id bigint not null,
  event_type varchar(64) not null,
  routing_key varchar(128) not null,
  payload text not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  max_retry_count int not null default 5,
  available_at datetime(6) not null,
  locked_at datetime(6),
  locked_by varchar(64),
  last_error varchar(500),
  sent_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_outbox_event_key unique(event_key)
);

create index idx_outbox_status_available on outbox_event(status, available_at);

create table shipment_anomaly (
  id bigint not null auto_increment primary key,
  shipment_id bigint not null,
  anomaly_type varchar(64) not null,
  business_key varchar(160) not null,
  severity varchar(16) not null,
  status varchar(32) not null,
  rule_code varchar(64) not null,
  evidence_event_id bigint,
  description varchar(500) not null,
  resolution_note varchar(500),
  detected_at datetime(6) not null,
  resolved_at datetime(6),
  version int not null default 0,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_anomaly_key unique(business_key),
  constraint fk_anomaly_shipment foreign key(shipment_id) references shipment(id)
);

create index idx_anomaly_status_type_detected on shipment_anomaly(status, anomaly_type, detected_at);

create table reconciliation_batch (
  id bigint not null auto_increment primary key,
  batch_no varchar(64) not null unique,
  trigger_type varchar(32) not null,
  status varchar(32) not null,
  total_count int not null,
  success_count int not null,
  failed_count int not null,
  difference_count int not null,
  inserted_event_count int not null,
  started_at datetime(6),
  completed_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null
);

create table reconciliation_task (
  id bigint not null auto_increment primary key,
  batch_id bigint not null,
  shipment_id bigint not null,
  task_key varchar(128) not null,
  status varchar(32) not null,
  retry_count int not null,
  max_retry_count int not null,
  next_retry_time datetime(6),
  before_status varchar(64),
  remote_status varchar(64),
  after_status varchar(64),
  difference_found boolean not null,
  inserted_event_count int not null,
  last_error varchar(500),
  locked_at datetime(6),
  locked_by varchar(128),
  started_at datetime(6),
  completed_at datetime(6),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  constraint uk_reconciliation_task unique(task_key),
  constraint fk_rt_batch foreign key(batch_id) references reconciliation_batch(id),
  constraint fk_rt_shipment foreign key(shipment_id) references shipment(id)
);

create index idx_rt_batch_status on reconciliation_task(batch_id, status);
create index idx_rt_status_next on reconciliation_task(status, next_retry_time);

create table operation_log (
  id bigint not null auto_increment primary key,
  operation_type varchar(64) not null,
  resource_type varchar(64) not null,
  resource_id bigint,
  operator_name varchar(64),
  request_id varchar(128),
  summary varchar(500),
  created_at datetime(6) not null
);
